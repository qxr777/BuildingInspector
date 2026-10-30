# 03 sync/upload 同步管线规格

- 文档版本：v1.0 ｜ 最后更新：2026-10-30 ｜ 基线 commit：`843c0626`
- 代码锚点：`bi-biz/src/main/java/edu/whut/cs/bi/biz/service/impl/SyncUploadServiceImpl.java`
  （下文仅给行号）；HTTP 投影见 [06](06-data-api-contract.md)，载荷字段全表见
  [APP 离线同步开发指南](../../doc/APP%20离线同步开发指南.md)。

## 1. 管线总览

入口 `SyncUploadServiceImpl#syncUpload(Map)`（:75，`@Transactional(rollbackFor=Exception)`，
:74）。载荷为裸 `Map<String,Object>`，**无同步专用 DTO**（REQ-SYNC-504）。

```
App 离线库 ──POST /api/v2/sync/upload──▶ ApiControllerV2（认证/用户识别，见 06）
                                            │
                                            ▼
              sync_log(status=0) → 十二段固定顺序处理 → sync_log(status=1)
                                            │ 逐行错误：收集进 SyncResultVo.errors，不回滚
                                            │ 致命异常：整批回滚，sync_log(status=2) 并抛出
                                            ▼
              成功且识别为 App 用户 → 触发 user.db 重打包，data=SqliteVo（REQ-SYNC-503）
```

### 1.1 顶层键（11 个数组 + 2 个信封键，共 13 个受识别键）

| # | 键名 | 段 | 处理方法 | 实体名（errors/bi_id_mapping） |
|---|---|---|---|---|
| 信封 | syncUuid、clientInfo | — | :76、:95（syncUuid 缺省服务端生成） | — |
| 1 | buildings | 1/3 | processBuildings :155；processBuildingsRootIds :536 | Building |
| 2 | objects | 2 | processBiObjects :187（五轮） | BiObject |
| 3 | components | 4 | processComponents :259（冻结段，REQ-SYNC-303） | Component |
| 4 | elements | 5 | processElements :298 | Element |
| 5 | diseases | 6 | processDiseases :338 | Disease |
| 6 | diseaseDetails | 7 | processDiseaseDetails :386 | DiseaseDetail |
| 7 | attachments | 8 | processAttachments :423 | Attachment |
| 8 | spanElementParts | 9 | processSpanElementParts :554 | SpanElementPart |
| 9 | assessManualInputs | 10 | processAssessManualInputs :609 | AssessManualInput |
| 10 | assessAbsentMarks | 11 | processAssessAbsentMarks :659 | AssessAbsentMark |
| 11 | assessSingleControlMarks | 12 | processAssessSingleControlMarks :719 | AssessSingleControlMark |

调用顺序固定（:112-142）：buildings → objects → **rootIds 回填**（夹在 objects 与 components
之间）→ components → elements → diseases → diseaseDetails → attachments → spanElementParts →
人工三数组。现场载荷 buildings 恒为空（REQ-SYNC-501），但段与回填逻辑保留。

## 2. 通用同步语义（REQ-SYNC-1xx）

#### REQ-SYNC-101 固定段序与信封处理
- ACTIVE ｜ P0 ｜ CODE ｜ 普遍式
- 需求陈述：系统应按第 1.1 节固定段序处理载荷；syncUuid 缺省时由服务端生成 UUID；每批以
  syncUuid 登记 bi_sync_log，并将 clientInfo 原样留痕。
- 验收准则：无 syncUuid 的合法载荷同步成功且 bi_sync_log 有该批记录；同批内后段可引用前段
  新插入行的服务端 id（uuidMap）。
- 代码锚点：:76-78、:91-101、:112-142
- 测试锚点：PostSyncSyncUploadE2ETest（SVC 系列）

#### REQ-SYNC-102 未知顶层键静默忽略
- ACTIVE ｜ P1 ｜ DEC:v1.6 ｜ 非预期式
- 需求陈述：如果载荷出现系统不认识的顶层键或实体字段，系统应静默忽略（fastjson 按目标类型
  反序列化丢弃多余键），不得致整个批次失败；旧 App 传已退役的 spanComponentParts 键同样忽略。
- 验收准则：含随机未知键与 spanComponentParts 键的载荷同步成功，已知数组正常落库。
- 代码锚点：`parseList` :819-823（fastjson 按目标类型丢弃多余字段）；段方法按具名键读取（:113-142，
  未读取的顶层键天然忽略）
- 测试锚点：无显式用例（07 T3 已登记）；现状由 fastjson/具名读取语义与全部 sync E2E 隐式保护

#### REQ-SYNC-103 按 offline_uuid 幂等 upsert 与 id 映射
- ACTIVE ｜ P0 ｜ CODE ｜ 普遍式
- 需求陈述：每个实体段应先以 offline_uuid 调 selectByOfflineUuid 云端回查：命中则更新该行，
  未命中则插入 is_offline_data=1 的新行；新插入行同步登记 bi_id_mapping 并回填本批 uuidMap，
  供同批后续段与子实体解析。
- 验收准则：同一 offline_uuid 连续两批同步，云端恰有一行且第二批为 update；同批"先父后子"
  （乱序到达时）经 uuidMap 正确挂接。
- 代码锚点：各段 existing 分支（如 :160-180、:211-246）；`saveMapping` :825-840
- 测试锚点：SVC-001~006（幂等/重放）

#### REQ-SYNC-104 逐行错误隔离
- ACTIVE ｜ P0 ｜ CODE ｜ 非预期式
- 需求陈述：如果单行处理抛出任意异常或值域校验失败，系统应捕获该错误写入
  SyncResultVo.errors（实体名 + offline_uuid + 原因）并继续处理同批其余行，而非整批回滚。
- 验收准则：一批 3 行其中 1 行外键缺失，成功 2 行落库、errors 恰 1 条且含该行 uuid。
- 代码锚点：各段 `catch → result.addError`（:181-183、:249-252、:450-453、:589-591 等）
- 测试锚点：SVC/HTTP 系列的逐行容错用例

#### REQ-SYNC-105 致命异常整批回滚与 sync_log 三态
- ACTIVE ｜ P0 ｜ CODE ｜ 非预期式
- 需求陈述：段方法外的致命异常（非逐行业务异常）应触发事务回滚，bi_sync_log.status 置 2 并
  记异常消息后重新抛出；正常完成置 1；开始时置 0。
- 验收准则：模拟段内未捕获异常后，本批已执行的写操作全部回滚，sync_log 末态为 2。
- 代码锚点：状态 0 :91-101；状态 1 :144；状态 2+rethrow :146-150
- 测试锚点：PostSyncSyncUploadE2ETest（失败批回滚用例）

#### REQ-SYNC-106 删除不存在的行幂等成功
- ACTIVE ｜ P1 ｜ CODE ｜ 事件式：当 offlineDeleted=1 的行在云端不存在时，系统应视为幂等成功
跳过（不报错、不计 successCount）；存在时按 REQ-SYNC-301 的删除语义表处理。
- 代码锚点：附件分支注释与实现 :435-445；其余实体删除分支（如 :174-176、:227-230、:583-585）
- 测试锚点：SVC 系列重复删除用例

## 3. UUID 解析与结构树（REQ-SYNC-2xx）

#### REQ-SYNC-201 bi_object 五轮父解析
- ACTIVE ｜ P0 ｜ CODE ｜ 事件式
- 需求陈述：当对象行携带 parentUuid 时，系统应在最多五轮迭代中解析父 id：每轮跳过父尚未就绪
  的行，已处理行不再重试；全部处理完或某轮零进展即结束。
- 验收准则：一批准根→UNIT→SPAN→PART 故意逆序排列，仍全部挂接到正确父 id；五轮后仍未解析的
  父不会造成半截插入（该行未被处理）。
- 代码锚点：五轮循环 :193-256；parentId 解析 :199-205；终止条件 :254-255
- 测试锚点：NewChainSyncUploadE2ETest（乱序父链，E2E-NEW-001/002）

#### REQ-SYNC-202 三类反查：本批映射优先 + 云端回查兜底
- ACTIVE ｜ P0 ｜ DEC:v1.6 ｜ 普遍式
- 需求陈述：系统应对 bi_object、bi_building、bi_element 三类 UUID 提供统一反查：先查本批
  uuidMap，未命中再 selectByOfflineUuid 回查云端既有行，命中后回填 uuidMap；其中 element
  回查仅命中 del_flag='0' 的活行。
- 验收准则：现场 SPAN 的 parentUuid 指向包内下发的云端 UNIT（本批不重传）时仍正确挂接
（E2E-APP-001）；已逻辑删除的 element UUID 反查为 null。
- 代码锚点：resolveBiObjectId :769-783；resolveBuildingId :786-800；
  resolveElementId :803-817
- 测试锚点：E2E-APP-001、E2E-NEW-002

#### REQ-SYNC-203 新节点行语义：is_offline_data、ancestors、无 building_id
- ACTIVE ｜ P0 ｜ DEC:v1.6 ｜ 普遍式
- 需求陈述：现场新增 bi_object 行应落 is_offline_data=1；ancestors 按父链拼接
  （"0,parentId,…"，根级为 "0"）；bi_object **不写 building_id/building_uuid**，对象→桥梁
  归属仅靠 bi_building.root_object_id 单向定位。
- 验收准则：E2E-APP-001 新增 SPAN/LAYER/PART 的 is_offline_data=1、ancestors 与父链一致，
  bi_object 无 building_id 列写入。
- 代码锚点：:209-210（裁决注释）、:233-244
- 测试锚点：E2E-APP-001、E2E-NEW-001

## 4. 逐段规则（REQ-SYNC-3xx）

#### REQ-SYNC-301 删除语义分表（逻辑删 vs 物理删）
- ACTIVE ｜ P0 ｜ CODE ｜ 普遍式
- 需求陈述：offlineDeleted=1 的删除行为按表固定，系统不得混用：

| 语义 | 表 | 实现 |
|---|---|---|
| 逻辑删（del_flag） | bi_building、bi_object、bi_component、bi_element | Mapper deleteXById 置 del_flag |
| 物理删（表无 del_flag） | bi_disease、bi_disease_detail、bi_attachment、bi_span_element_part、bi_assess_manual_input、bi_assess_absent_mark、bi_assess_single_control_mark | Mapper deleteXById 物理 DELETE |

- 验收准则：删除一个 element 后 selectByOfflineUuid 不命中但行仍在库（del_flag=2/1）；删除一条
  disease 后物理不可查；附件删除注释明确"表无 del_flag"（:420、:552）。
- 代码锚点：building :167、object :220、component :273、element :316（逻辑）；disease :365、
  detail :396、attachment :441、spanElementPart :576、manual :634、absent :694、
  singleControl :744（物理）
- 测试锚点：E2E-SVC（删除分支）、E2E-ATT-005（物理删除后云端回查）

#### REQ-SYNC-302 building.root_object_id 回填
- ACTIVE ｜ P2 ｜ CODE ｜ 事件式
- 需求陈述：当建筑物行的 rootObjectUuid 与 buildingUuid 都能在 uuidMap 解析时，系统应在
  objects 段之后用单独的轻量 UPDATE 回填 root_object_id。
- 验收准则：云端建桥链批中 root 节点与 building 同批到达时，回填后
  bi_building.root_object_id 非空；现场 buildings=[] 时本段无操作。
- 代码锚点：processBuildingsRootIds :536-548（段调用 :119）
- 测试锚点：E2E-HTTP-005/008（PostUpload-Test6/Test11 载荷带 rootObjectUuid）

#### REQ-SYNC-303 components 第四段冻结保留
- ACTIVE ｜ P2 ｜ DEC（bi_component 生产链退役另开任务，REQ-BIZ-905②）｜ 状态式
- 需求陈述：processComponents 段保持旧 bi_component 链的 upsert/逻辑删能力以兼容存量生产数据，
  但 5230 新链（elements/spanElementParts）不读该段结果；段的行为变更不在本批规格承诺内。
- 代码锚点：processComponents :259-296
- 测试锚点：无新链测试（旧链用例已随退役清理）；状态以本条冻结为准

#### REQ-SYNC-304 disease / diseaseDetail 外键解析
- ACTIVE ｜ P0 ｜ CODE ｜ 普遍式
- 需求陈述：disease 行应将 elementUuid/objectUuid 等引用经反查回填为服务端 id；diseaseDetail
  行应将 diseaseUuid 解析为病害服务端 id；任一引用解析失败按 REQ-SYNC-104 逐行报错不落库。
- 验收准则：detail 先于 disease 乱序到达且当批无法解析时，该行进入 errors 而非悬空外键。
- 代码锚点：processDiseases :338-383；processDiseaseDetails :386-420
- 测试锚点：E2E-NEW-002（病害/详情挂接）

#### REQ-SYNC-305 附件按 type 路由挂载主体
- ACTIVE ｜ P0 ｜ DEC:v1.2 ｜ 普遍式
- 需求陈述：第 8 段应按附件 type 路由 subject：type=6 → bi_building（桥梁正/侧立面），
  type=8 → bi_object（PART 当前照片），type 为 null/1/7 → bi_disease（普通/AD 病害照片）；
  主体解析顺序为本批 uuidMap → 云端 selectByOfflineUuid；offlineSubjectUuid 缺失时兼容载荷
  直带服务端 subject_id。
- 验收准则：同批三类附件分别挂到桥梁、PART、病害；缺 offlineSubjectUuid 而带 subject_id 的
  旧载荷仍可落库。
- 代码锚点：resolveAttachmentSubject :505-529；isDiseaseAttachment :532-534；
  ATTACHMENT_TYPE_BUILDING/PART 常量；路由失败错误 :449-454
- 测试锚点：E2E-ATT-001~004、E2E-APP-001（病害照片）

#### REQ-SYNC-306 minioId 必填与 attachment_count 重算
- ACTIVE ｜ P0 ｜ DEC:v1.2 ｜ 非预期式
- 需求陈述：如果附件行缺 minioId（未走通道 A 预上传）或挂载主体反查失败，系统应判逐行错误、
  不落库（避免 NOT NULL 底层 SQL 异常）；每批结束时，系统应对本批触及的病害
  （新增/更新/删除/改挂的旧主体与新主体）按 bi_attachment 实际行数统一重算
  bi_disease.attachment_count。
- 验收准则：缺 minioId 行进 errors 且 bi_attachment 无该行；删除一张病害照片后
  attachment_count 与实际行数一致（E2E-ATT 系列断言两侧相等）。
- 代码锚点：缺 minioId :457-462；name/type 兜底 :463-469；touchedDiseaseIds 收集 :438-439、
  :473-474、:487-489；recountAttachmentCount :495-497
- 测试锚点：E2E-ATT-006~011

#### REQ-SYNC-307 spanElementPart 三 UUID 硬校验
- ACTIVE ｜ P0 ｜ CODE ｜ 非预期式
- 需求陈述：锚定行的 elementUuid/spanUuid/partUuid 应分别反查 element 与 bi_object 服务端 id；
  如果三者任一为 null（存在未同步实体），系统应逐行报错不落库；自然唯一键
  (element,span,part) 由表约束兜底，重复锚定不产生第二行。
- 验收准则：缺 PART 的锚定行进 errors"element/span/part 反查失败"；完整行重复同步仍一行。
- 代码锚点：三解析 :561-566；硬校验 :587-592；upsert :568-598
- 测试锚点：E2E-NEW-003（缺锚定拒绝）、E2E-NEW-002（正常锚定）

#### REQ-SYNC-308 审计字段与离线标记统一
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式：新增行应统一写 createBy/createTime（loginName，取不到 Shiro
会话时为 "system"）、is_offline_data=1；更新行写 updateBy/updateTime。
- 代码锚点：loginName 缺省 :103-107；新增分支如 :177-179、:242-244、:481-485、:594-596、
  :644-646
- 测试锚点：E2E-SVC（create_by 断言）

## 5. 人工判定三数组（REQ-SYNC-4xx）

三表是唯一写入通道（REQ-BIZ-104）；值域规则本身见 [02 REQ-EVAL-701](02-assessment-engine-spec.md)。

#### REQ-SYNC-401 落库前行校验
- ACTIVE ｜ P0 ｜ CODE ｜ 普遍式：三数组每行应先经 AssessInputRowValidator 校验；存在任一校验
错误时该行进入 errors（错误以 "; " 拼接），不落库。
- 代码锚点：manual :616-620、absent :666-670、singleControl :726-730
- 测试锚点：SyncUploadAssessMarksTest 10 例

#### REQ-SYNC-402 主体反查失败阻断该行
- ACTIVE ｜ P0 ｜ CODE ｜ 非预期式
- 需求陈述：manual 的 elementUuid、absent 的 unitUuid/spanUuid（非空时）反查失败，应判逐行
  错误不落库（manual 错误文案"构件可能未同步"），不得产生悬挂外键。
- 代码锚点：manual :621-626；absent :671-686
- 测试锚点：SyncUploadAssessMarksTest（反查失败用例）

#### REQ-SYNC-403 单项控制证据反查 best-effort
- ACTIVE ｜ P1 ｜ CODE ｜ 可选式
- 需求陈述：在 singleControl 行通过值域校验的前提下，其 spanUuid/elementUuid 证据引用反查
  失败时不应阻断落库——证据仅用于留痕，命中已由 indicatorNo+hit 表达；反查到的 id 回填。
- 验收准则：命中行携带不存在的 elementUuid 仍落库，element_id 为 null，hit=1 仍驱动
  REQ-EVAL-503 覆盖。
- 代码锚点：:731-736（无错误分支、直接回填）
- 测试锚点：SyncUploadAssessMarksTest；E2E-5230-003

#### REQ-SYNC-404 三表行级幂等与物理删除
- ACTIVE ｜ P0 ｜ CODE ｜ 事件式：三表均按 offline_uuid 行级 upsert、按 offlineDeleted=1
物理删（表无 del_flag）；删除不存在的行幂等成功（REQ-SYNC-106）。
- 代码锚点：manual :627-648、absent :687-708、singleControl :737-758
- 测试锚点：SyncUploadAssessMarksTest（重复同步/删除重放）

#### REQ-SYNC-405 行级归属，不做任务级替换
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式
- 需求陈述：三表同步以 offline_uuid 为粒度逐行合并，taskId 随行携带；系统**不**因一次同步
而按 task_id 整体替换某任务的全部人工行（与评定结果四表的 task 级先删后插
REQ-EVAL-713 形成对照）。
- 验收准则：分两批同步同一 taskId 的不同 manual 行，两批后两行均在。
- 代码锚点：:613-648（循环内逐行 selectByOfflineUuid）
- 测试锚点：SyncUploadAssessMarksTest；E2E-APP-001（两批同步）

## 6. 业务边界与响应（REQ-SYNC-5xx）

#### REQ-SYNC-501 buildings 恒为空（现场链路）
- ACTIVE ｜ P0 ｜ DEC:2026-10-27 ｜ 状态式：现场 App 的载荷在业务上必须 buildings=[]，
系统对 bi_building 零回写；管线保留 buildings 处理能力仅供云端链路。需求级表述与验收见
REQ-BIZ-102。
- 代码锚点：:113（能力保留）；E2E 固定空数组（AppOfflineWorkflowE2ETest 载荷提取）
- 测试锚点：**E2E-APP-001 零回写断言**

#### REQ-SYNC-502 同步不触发评定计算
- ACTIVE ｜ P0 ｜ DEC:2026-10-27 ｜ 普遍式：sync/upload 成功后系统不得自动触发 5230 calculate；
四张结果表的新增只能来自显式评定（REQ-BIZ-103）。
- 代码锚点：:109-153（全方法无 calculate 调用）；调用方仅持久层
- 测试锚点：E2E-APP-001 Stage 5→6（同步后四表无行，显式 calculate 后才有）

#### REQ-SYNC-503 同步成功后闭环重打包
- ACTIVE ｜ P1 ｜ CODE ｜ 事件式
- 需求陈述：当同步全段成功且调用方能被识别为 App 用户时，系统应触发 user.db 重打包并在 HTTP
响应 data 返回新的 SqliteVo（下载信息）；用户无法识别时返回 SyncResultVo；重打包异常不回滚
已提交的同步事务，按错误信封返回（HTTP 投影见 REQ-API-202；包内容见 [05](05-offline-package-spec.md)）。
- 代码锚点（控制器侧）：`ApiControllerV2#syncUpload`
  （bi-api `controller/ApiControllerV2.java:145-174`，成功重打包 :157-159、null 错误 :161、
  重打包异常 :166、未识别用户 :169）
- 测试锚点：E2E-APP-001（响应含新包信息）、ApiControllerV2Test

#### REQ-SYNC-504 裸 Map 载荷契约冻结
- ACTIVE ｜ P1 ｜ DEC ｜ 普遍式
- 需求陈述：sync/upload 入参应保持裸 `Map<String,Object>` + fastjson 转 domain 的形态，本期不
引入同步专用 DTO；字段级契约以 APP 指南字段全表加本规格锚点共同构成，字段增删必须同 PR 更新
两处。
- 代码锚点：:75（签名）；`parseList` :819-823
- 测试锚点：全部 sync E2E（契约保护）

## 篇末追溯矩阵

| 需求 ID | 代码符号锚点 | 主要测试锚点 | 验收状态 |
|---|---|---|---|
| REQ-SYNC-101 | syncUpload :75-153 | SVC 系列 | E2E |
| REQ-SYNC-102 | parseList :819-823、具名读取 :113-142 | 无显式用例（T3 登记） | 仅手工 |
| REQ-SYNC-103 | 各段 existing 分支、saveMapping :825 | SVC-001~006 | E2E |
| REQ-SYNC-104 | 各段 addError | SVC/HTTP 容错用例 | E2E |
| REQ-SYNC-105 | sync_log 三态 :91/:144/:146 | PostSync 失败回滚用例 | E2E |
| REQ-SYNC-106 | 删除分支（:435-445 等） | SVC 重复删除 | E2E |
| REQ-SYNC-201 | 五轮循环 :193-256 | E2E-NEW-001/002 | E2E |
| REQ-SYNC-202 | resolveBiObjectId/BuildingId/ElementId :769-817 | E2E-APP-001 | E2E |
| REQ-SYNC-203 | :209-244（ancestors/is_offline_data） | E2E-APP-001、NEW-001 | E2E |
| REQ-SYNC-301 | 逻辑/物理删除各 mapper 调用 | SVC、E2E-ATT-005 | E2E |
| REQ-SYNC-302 | processBuildingsRootIds :536-548 | E2E-HTTP-005/008 | E2E |
| REQ-SYNC-303 | processComponents :259-296 | 无新链覆盖（冻结项） | 仅手工 |
| REQ-SYNC-304 | processDiseases/Details :338-420 | E2E-NEW-002 | E2E |
| REQ-SYNC-305 | resolveAttachmentSubject :505-529 | E2E-ATT-001~004、APP-001 | E2E |
| REQ-SYNC-306 | :457-462、recountAttachmentCount :495-497 | E2E-ATT-006~011 | E2E |
| REQ-SYNC-307 | :561-592 | E2E-NEW-003、NEW-002 | E2E |
| REQ-SYNC-308 | :103-107、各 create/update 分支 | SVC 审计断言 | E2E |
| REQ-SYNC-401 | Validator 调用 :616/:666/:726 | SyncUploadAssessMarksTest 10 例 | 单测+E2E |
| REQ-SYNC-402 | :621-626、:671-686 | SyncUploadAssessMarksTest | 单测 |
| REQ-SYNC-403 | :731-736 | SyncUploadAssessMarksTest、E2E-5230-003 | 单测+E2E |
| REQ-SYNC-404 | 三表 upsert/删除分支 | SyncUploadAssessMarksTest | 单测 |
| REQ-SYNC-405 | :613-648 | SyncUploadAssessMarksTest、E2E-APP-001 | 单测+E2E |
| REQ-SYNC-501 | :113 + E2E 固定空数组 | **E2E-APP-001** | E2E |
| REQ-SYNC-502 | syncUpload 全方法无 calculate | E2E-APP-001 Stage 5→6 | E2E |
| REQ-SYNC-503 | ApiControllerV2:145-174；SqliteService:114 | E2E-APP-001、ApiControllerV2Test | 单测+E2E |
| REQ-SYNC-504 | syncUpload(Map) :75 | 全部 sync E2E | E2E |
