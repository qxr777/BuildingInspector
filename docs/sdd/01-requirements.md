# 01 业务需求与边界

- 文档版本：v1.0 ｜ 最后更新：2026-10-30 ｜ 基线 commit：`843c0626`

本篇定义业务级需求（REQ-BIZ-0xx）、2026-10-27 拍板的业务边界裁决（REQ-BIZ-1xx）与本期
不做项（REQ-BIZ-9xx）。子系统级可验证需求在 02–06 展开。

## 1. 业务背景

检测人员携带 App 在无网络的桥梁现场完成检查：在已下发的评定单元下增补桥跨与部件、在构件页
录入缺损与人工判定、为病害拍照；回到有网环境后分批同步；随后由后端按 JTG/T 5230—2026 完成
技术状况评定并供 Web 端查询。桥梁的创建与任务下发发生在云端 Web 端，不在现场。

## 2. 云端 / 现场职责划分（业务边界，DEC:2026-10-27）

| 事项 | 云端 Web | 现场 App（离线） |
|---|---|---|
| bi_building（桥梁记录） | 创建/编辑 | 不创建、不更新、不删除（sync 的 `buildings` 恒为 `[]`） |
| ROOT / UNIT 结构节点 | 创建，随离线包下发（带 server_id、is_offline_data=0） | 只读引用，不重传 |
| SPAN / LAYER / PART | — | 在已下发 UNIT 下新增（is_offline_data=1） |
| 5230 检查任务 | 创建并下发（std_version=5230-2026） | 只读，按 task_id 归属采集数据 |
| 构件/锚定/病害/详情/照片/人工三表 | 经 sync 回写 | 离线新增、拍照、录入 |
| 评定计算 calculate | 后端内部触发 | 不调用；sync 也不自动触发 |

锁定证据：E2E-APP-001 在两批同步后断言 bi_building 的 name/root_object_id/new_root_object_id
零变更（`AppOfflineWorkflowE2ETest#testFieldAddSpansParts_captureDisease_sync_thenBackendAssessment`）；
样例库见 `app-end/README.md`。

## 3. 业务级需求

#### REQ-BIZ-001 5230 四级技术状况评定
- ACTIVE ｜ P0 ｜ 来源：STD:3.2–3.4 ｜ 普遍式
- 需求陈述：系统应对桥型为 B01–B07 的 5230 任务，按"构件→桥跨→评定单元→全桥"四级完成技术状况
  评定，并输出四张结果表；公式、分档与覆盖规则逐条可追溯（见 [02](02-assessment-engine-spec.md)）。
- 验收准则：给定标准附录 C 算例输入，WHEN 执行评定 THEN 结果与 golden 表逐项一致
  （[07 §2](07-verification-traceability.md)，含 85.3/70.3/84.5 与现场链 70.0/72.5）。
- 代码锚点：`Assess5230CalculateService#calculate`（bi-biz `service/std/evaluation/Assess5230CalculateService.java:180`）
- 测试锚点：评定包 8 测试类 57 例；E2E-5230-001~004、E2E-APP-001

#### REQ-BIZ-002 离线采集载荷幂等回写
- ACTIVE ｜ P0 ｜ 来源：CODE ｜ 普遍式
- 需求陈述：系统应接收 App 的 sync/upload 载荷，按 offline_uuid 幂等合并入云端库，支持乱序、
  重复提交、逐行容错与离线删除（详见 [03](03-sync-pipeline-spec.md)）。
- 验收准则：同一 syncUuid/同一 offline_uuid 重放不产生重复行；单行非法不影响同批其他行。
- 代码锚点：`SyncUploadServiceImpl#syncUpload`（bi-biz `service/impl/SyncUploadServiceImpl.java:75`）
- 测试锚点：E2E-SVC-001~006、E2E-HTTP-001~010、E2E-NEW-001~004、E2E-CL-001~005

#### REQ-BIZ-003 病害照片的预上传与挂接
- ACTIVE ｜ P1 ｜ 来源：DEC:v1.2–v1.4 ｜ 普遍式
- 需求陈述：系统应支持照片在 sync JSON 之前独立预上传（uploadUuid 幂等、服务端缩略图、批量、回查），
  并在 sync 第 8 段按主体类型挂接到病害/桥梁/PART（详见 [04](04-attachment-spec.md)）。
- 验收准则：重传同一 uploadUuid 零流量返回既有结果；同步后 bi_disease.attachment_count 与
  bi_attachment 实际行数一致。
- 代码锚点：`SyncAttachmentServiceImpl#preUpload`（bi-biz `service/impl/SyncAttachmentServiceImpl.java:64`）
- 测试锚点：E2E-ATT-001~011

#### REQ-BIZ-004 三类离线包与同步后闭环重打包
- ACTIVE ｜ P1 ｜ 来源：CODE ｜ 事件式
- 需求陈述：当用户拉取或 sync/upload 全段成功时，系统应生成/重建用户级 user.db（14 表）并返回下载
  信息；并提供 common 字典包与单桥 ZIP 包（详见 [05](05-offline-package-spec.md)）。
- 验收准则：sync/upload 成功响应 data 为重打包后的 SqliteVo；包内业务行带 server_id 且
  is_offline_data=0；四张评定结果表不出任何包。
- 代码锚点：`SqliteService#generateUserSqliteSync`（bi-biz `service/impl/SqliteService.java:114`）；
  `ApiControllerV2#syncUpload`（bi-api `controller/ApiControllerV2.java:145`）
- 测试锚点：SqliteServiceTest 9 例；E2E-APP-001

#### REQ-BIZ-005 评定全过程留痕
- ACTIVE ｜ P1 ｜ 来源：STD:3.2.3/3.3.3/3.4.5 ｜ 普遍式
- 需求陈述：系统应对评定过程留痕：构件分项指数来源（AUTO/MANUAL/ABSENT）、3.3.3 覆盖的原值与
  触发构件、单元合成中间量（min/max/avg/lim/worstSpan）、全桥分层值与命中的单项控制编号、
  非阻断 warnings。
- 验收准则：结果四表行与 calculate 响应 warnings 可复核任一定级结论；golden 与 E2E 断言
  override_trigger/single_control_hits/bsci_raw 等留痕字段非空且正确。
- 代码锚点：`Assess5230CalculateService#toSpanRow/toUnitRow/toBridgeRow`（:557、:576、:593）
- 测试锚点：E2E-5230-002/003；SpanEvaluatorTest、BridgeAggregatorTest

### 3.1 业务边界裁决（强约束）

#### REQ-BIZ-101 桥梁本体仅云端创建并随包下发
- ACTIVE ｜ P0 ｜ 来源：DEC:2026-10-27 ｜ 状态式
- 需求陈述：当对象为桥梁本体（bi_building、ROOT 节点、UNIT 节点、5230 任务）时，其只能在云端
  Web 端创建，并作为用户离线包的下发行（带 server_id、is_offline_data=0）到达 App。
- 验收准则：离线包内 ROOT/UNIT/bi_building/任务均为服务端行；现场链路不产生任何上述实体的插入。
- 代码锚点：`SqliteService#insertBiObjects/insertBuildings`（bi-biz `service/impl/SqliteService.java`，出包行语义）
- 测试锚点：E2E-APP-001 Stage 1；`app-end/user_stage1_span_part_added.db`（ROOT/UNIT server_id 9201–9203，flag 0）

#### REQ-BIZ-102 现场仅增补 SPAN/LAYER/PART，bi_building 零回写
- ACTIVE ｜ P0 ｜ 来源：DEC:2026-10-27 ｜ 非预期式
- 需求陈述：如果现场数据涉及结构新增，则新增节点只能是已下发 UNIT 之下的 SPAN/LAYER/PART；
  sync/upload 的 `buildings` 必须恒为空数组，系统不得对 bi_building 产生任何回写（而非允许现场
  传 building 行触发 update/new_root_object_id 回填）。
- 验收准则：① E2E-APP-001 两批同步后 bi_building 全部字段与同步前逐字段一致；② 现场 SPAN 的
  parentUuid 指向下发 UNIT 时，经管线云端 UUID 回查正确挂接（REQ-SYNC-202）。
- 代码锚点：管线保留 processBuildings 能力但现场载荷不传（`SyncUploadServiceImpl.java:113`）；
  E2E 固定 `buildings=[]`（`AppOfflineWorkflowE2ETest#extractPayloadFromAppDb`）
- 测试锚点：E2E-APP-001（两批同步后的零回写断言）

#### REQ-BIZ-103 calculate 仅后端内部触发
- ACTIVE ｜ P0 ｜ 来源：DEC:2026-10-27 ｜ 普遍式
- 需求陈述：评定计算只能由后端内部触发（Web 操作或后端账号），App 不调用 calculate，sync/upload
  成功也不自动触发计算。
- 验收准则：① /api/v2 下不存在 calculate 端点；② sync/upload 全段成功后四张结果表无新增行；
  ③ 显式 calculate 后结果才出现（E2E-APP-001 Stage 6）。
- 代码锚点：`Assess5230Controller` 类 Javadoc 与端点集（bi-biz `controller/Assess5230Controller.java:25-36`）；
  管线十二段无 calculate 调用（`SyncUploadServiceImpl.java:112-142`）
- 测试锚点：E2E-APP-001 Stage 5→6

#### REQ-BIZ-104 人工判定三表单通道
- ACTIVE ｜ P0 ｜ 来源：DEC:v1.4（2026-10-27 拍板，覆盖早期双通道设计）｜ 普遍式
- 需求陈述：人工判定三表（manual_input / absent_mark / single_control_mark）的唯一写入通道是
  App 经 sync/upload 的三数组；系统不提供 Web/REST 直连录入与删除端点。
- 验收准则：① 代码库不存在 Assess5230InputService 与录入端点（commit aef3ea2a 已删）；
  ② 三表行只能由管线第 10–12 段落库。
- 代码锚点：`SyncUploadServiceImpl#processAssessManualInputs/AbsentMarks/SingleControlMarks`
  （:609、:659、:719）；`Assess5230Controller` 仅 5 端点
- 测试锚点：SyncUploadAssessMarksTest 10 例；E2E-NEW/APP 系列

## 4. 本期不做项（REQ-BIZ-9xx，含复活条件）

不做项是已裁决的范围边界，不是缺陷登记；复活需走规格 MINOR 版本（见 README §7）。

| ID | 不做项 | 现状行为 | 复活前置条件 |
|---|---|---|---|
| REQ-BIZ-901 | EDDI 材料耐久性缺损自动判定（STD 第 4 章） | EDDI 由人工录入 0–2，缺省 0，来源 AUTO/MANUAL | bi_material_durability 字典与判定规则齐备 + E2E |
| REQ-BIZ-902 | EFI/EAI 的非 EDI 病害类型自动判定（EFI 见第 14 章） | EFI/EAI 人工录入；引擎仅消费 scale_type='EDI' 的病害计 ESDI | EFI/EAI 病害类型字典（scale_type）+ 种子 + E2E |
| REQ-BIZ-903 | B08–B21 桥型评定（拱/组合/悬索/斜拉，共 14 型） | meta.yml 落根桩 `structureComplete:false`；calculate 硬拒绝且不落结果 | 对应 templates + main-parts + 种子 + E2E 四件套齐备 |
| REQ-BIZ-904 | 附录 C 评定报告导出 | 无导出端点 | 报告模板裁决 + 导出实现 + 验收 |
| REQ-BIZ-905 | 其余明确不做（4 项合并） | 见下 | 各项独立裁决 |

REQ-BIZ-905 分项：

1. **App 端 5230 人工判定输入页面**——本期 App 输入页明确不做（现场数据由 E2E/样例库与后续
   App 迭代承载）；
2. **bi_component 生产链退役**——生产 bi_component 表、管线第 4 段（冻结）、Web 病害 CRUD、旧 H21
   评分、报告/CBMS 依赖保留，另开任务；仅测试链与 bi_span_component_part 已退役清理；
3. **预上传孤儿文件 GC**——bi_file_upload_registry 备有 create_time 索引，孤儿回收策略未实现；
4. **附件通道 B（/api/v2/sync/attachment/oss）**——端点保留但暂停，不在本期验收范围。

## 5. 优先级与批次

- P0（验收阻断）：REQ-BIZ-001、002、101、102、103、104，以及 02–04 中全部公式/分档/硬拒绝/
  幂等/删除语义条目；
- P1（本期必须）：REQ-BIZ-003、004、005 及附件/离线包/留痕主体条目；
- P2：兼容留痕行为（旧裸 minioId 路径、components 冻结段、错误信封细节等）。

## 篇末追溯矩阵

| 需求 ID | 主要代码锚点 | 主要测试锚点 | 验收状态 |
|---|---|---|---|
| REQ-BIZ-001 | Assess5230CalculateService#calculate | 评定包 57 例、E2E-5230-001~004 | 单测+E2E |
| REQ-BIZ-002 | SyncUploadServiceImpl#syncUpload | E2E-SVC/HTTP/NEW/CL 共 25 例 | E2E |
| REQ-BIZ-003 | SyncAttachmentServiceImpl#preUpload | E2E-ATT-001~011 | 单测+E2E |
| REQ-BIZ-004 | SqliteService#generateUserSqliteSync | SqliteServiceTest、E2E-APP-001 | 单测+E2E |
| REQ-BIZ-005 | CalculateService 行映射 + EvalWarnings | E2E-5230-002/003 | 单测+E2E |
| REQ-BIZ-101 | SqliteService 出包行语义 | E2E-APP-001 Stage 1 | E2E |
| REQ-BIZ-102 | SyncUploadServiceImpl:113 + 固定空 buildings | E2E-APP-001 零回写断言 | E2E |
| REQ-BIZ-103 | Assess5230Controller 端点集 | E2E-APP-001 Stage 5→6 | E2E |
| REQ-BIZ-104 | SyncUploadServiceImpl:609/659/719 | SyncUploadAssessMarksTest 10 例 | 单测+E2E |
| REQ-BIZ-901~905 | 不做项（见各现状行；B08 闸门见 REQ-EVAL-604） | E2E-5230-004（B08 拒绝） | 部分有反向 E2E |
