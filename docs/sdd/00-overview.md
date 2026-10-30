# 00 总览

- 文档版本：v1.0 ｜ 最后更新：2026-10-30 ｜ 基线 commit：`843c0626`
- 适用标准：JTG/T 5230—2026（stdVersion 常量值 `"5230-2026"`）

## 1. 背景

JTG/T 5230—2026《公路桥梁技术状况评定标准》于 2026-08-26 发布、2026-11-01 实施，替代
JTG/T H21—2011（标准元数据见 `bi-biz/src/main/resources/std/5230-2026/meta.yml:3-8`）。
正式稿相对报批稿取消了四个结构指数，改为"构件→桥跨→评定单元→全桥"四级单公式链与插值聚合，
旧 H21 评分链（bi_component / bi_evaluation / 旧 H21 引擎）不能承载新规范。

本批工作（2026-10-13 至 2026-10-30，dev/add-v2-api 分支）交付三件事：

1. **5230 评定引擎**：YAML 权威目录 + 四级纯计算核心 + 人工判定三表 sync 单通道 + 四张结果表；
2. **离线闭环**：云端建桥/任务下发 → 三类离线包 → App 现场增补与采集 → sync/upload 十二段管线
   幂等回写 → 成功后重打包 user.db；
3. **附件通道 A**：uploadUuid 幂等预上传（含服务端缩略图、批量、回查）+ sync 第 8 段按主体类型挂接。

### 1.1 目标

- 对 B01–B07 梁桥的 5230 任务，给出与正式稿条款逐式对应的、可复算可追溯的评定结果；
- App 现场离线作业（无网络建跨、录构件、拍病害）可幂等同步，重复上传/乱序/部分失败行为确定；
- 每条业务规则至少有一个代码锚点与一个自动化测试锚点（缺口显式登记，不靠口头保证）。

## 2. 范围

### 2.1 本期包含

- 评定：构件（3.2）、B.1.3 权重重分配、桥跨（3.3，含 3.3.3 覆盖）、评定单元（3.4.1–3.4.3）、
  全桥（3.4.4/3.4.5）；应设未设幻影件；16 项单项控制；共用构件；多评定单元；
- 桥型：B01–B07（meta.yml 中 `structureComplete: true` 的 7 类梁桥）；
- 新链：bi_object 新树（ROOT/UNIT/SPAN/LAYER/PART）＋bi_element＋bi_span_element_part；
- 同步：十二段管线、人工判定三数组、附件通道 A、三类离线包；
- 验证：引擎包 57 个单测（含 3 个标准附录 C 官方算例）与 41 个 E2E（@DisplayName 计）。

### 2.2 本期排除

详见 [01 §4](01-requirements.md) REQ-BIZ-901~905：EDDI 材料耐久自动判定、非 EDI 的 EFI/EAI
病害类型自动判定、B08–B21 桥型、附录 C 报告导出、App 端录入页、bi_component 生产链退役、
预上传孤儿 GC、附件通道 B。旧 H21 链冻结保留，不在本规格集内。

## 3. 系统上下文

### 3.1 角色与触发方

| 角色 | 入口 | 可触发的行为 |
|---|---|---|
| Web 管理端（浏览器会话） | `/biz/**`（Shiro 会话 + @RequiresPermissions） | 建桥、建 5230 任务、tree/inputs/result 查询、calculate 触发（后端内部操作人取登录名） |
| App（现场） | `/api/v2/**`（JWT Bearer，`/jwt/login` 取 token） | 拉取离线包、附件预上传、sync/upload 提交；**不调用 calculate** |
| 后端内部 | Assess5230Controller/Service | calculate（操作人可为登录名或 `"system"`） |
| 基础设施 | — | MySQL（业务库）、MinIO（附件原图/缩略图/user.db 包） |

### 3.2 上下文图

```
 Web 管理端                App（现场，离线作业）
     │  Shiro 会话                │  JWT
     ▼                             ▼
 /biz/assess5230/**        /api/v2/sync/**、/api/v2/{user,common,building}/**
     │                             │
     └──────────┬──────────────────┘
                ▼
        Spring Boot 业务后端（bi-biz / bi-api）
   ┌──────────────┼──────────────────────────┐
   ▼              ▼                            ▼
5230 引擎    sync/upload 十二段管线       SqliteService 离线包
(四级纯计算)  → 幂等回写 MySQL            → MinIO(user/common/单桥)
                → 附件第8段挂接            附件预上传 → MinIO + bi_file_map
                │                            (+bi_file_upload_registry)
                ▼
   MySQL：业务表 + bi_assess_ 六表 + bi_sync_log/bi_id_mapping
```

### 3.3 子系统与需求前缀

见 [README §3](README.md) 的 ID 区间表。

## 4. 约束与技术决策

| 约束 | 内容 |
|---|---|
| 运行时 | JDK 17；Spring Boot 2.5.15；MyBatis(XML/注解 SQL)；Shiro + JWT 双认证域 |
| 构建 | 根 `pom.xml`（com.ruoyi:4.8.0 多模块）；JDK17 固定 `JAVA_HOME=/Users/qixin/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home` |
| 载荷形态 | sync/upload 入参为裸 `Map<String,Object>`，**无同步专用 DTO**，fastjson 转 domain；字段契约靠规格＋E2E 保护 |
| 计算触发 | calculate 仅后端内部触发（DEC:2026-10-27）；App 不调、sync 不自动触发 |
| 事务边界 | syncUpload `@Transactional(rollbackFor=Exception)`：逐行错误不回滚，致命异常整批回滚；calculate `@Transactional` 四表先删后插 |
| 数值 | 全程 BigDecimal，禁止 double 直转；统一 1 位小数 HALF_UP（REQ-EVAL-801） |
| 离线存储 | SQLite，PRAGMA journal_mode=WAL、synchronous=NORMAL；业务行 offline_uuid 作主键 |
| DDL 治理 | 唯一允许手改的 DDL 文件是 `sql/test_env_all_in_one.sql`（幂等、按"部分"演进）；`sql/bi_schema.sql` 为生产快照禁手改；生产库变更由人工执行，禁止自动连生产 |
| 模块布局 | 引擎与评定端点在 **bi-biz**（`edu.whut.cs.bi.biz`，含 Assess5230Controller）；App V2 API 在 **bi-api**；E2E 在 **ruoyi-admin** |

## 5. 术语表

| 术语 | 含义 |
|---|---|
| ROOT / UNIT / SPAN / LAYER / PART | 新标结构树五级节点：全桥根 / 评定单元 / 桥跨 / 结构层 / 部件；构件 Element 挂在 PART 下（bi-biz `UnitStructureResolver#NODE_*`） |
| 构件（Element） | 评定的最小对象，一个 PART 可划多个构件；经 bi_span_element_part 锚定到"跨×部件" |
| 幻影件（应设未设） | 按规范应设置但现场无构件的部件：无 PART 节点时由 absent 标记的 partKey 从目录注入，partId 恒为 null，EDI=ECI=ECL=3 |
| γ（gamma） | 结构层影响系数（桥面系/上部/下部/附属），桥型模板给定 |
| ω / ω′（omega/omegaEffective） | 部件权重 / B.1.3 再分配后的有效权重（整数百分比；ω′ 为 4 位小数） |
| EDDI/ESDI/EDI | 耐久性缺损标度(0–2) / 结构性缺损标度(0–3) / 缺损状况标度(0–3，=max 前两者) |
| EFI/EAI | 功能状况标度(0–2) / 影响状况标度(–1/0/1，仅主要部件非 0) |
| ECI/ECL | 构件技术状况标度(0–5) / 构件技术状况等级(1–5) |
| CI | 部件 j 的构件技术状况标度＝部件内全部构件 ECI 的最大值 |
| BSCI/BSCL | 桥跨技术状况指数/等级 |
| BCI/BCL | 评定单元或全桥的技术状况指数/等级 |
| 人工三表 | bi_assess_manual_input / absent_mark / single_control_mark |
| 结果四表 | bi_assess_element_result、bi_span_evaluation（复用并补 unit_id）、bi_assess_unit_result、bi_assess_bridge_result |
| 三类离线包 | user 包（14 表业务数据）、common 包（11 表 5230 字典）、单桥包（7 表 ZIP） |
| 通道 A / B | A＝附件预上传 + sync 元数据挂接（在役）；B＝/oss ZIP 批量通道（端点保留，暂停） |
| 锚定 | bi_span_element_part 的 (element, span, part) 三元组把构件归入具体桥跨的部件 |
| golden | 标准附录 C 官方算例的手算期望值，用作公式类需求的验收物证 |

## 6. 需求状态机与条目模板

### 6.1 状态机

```
DRAFT ──评审通过──▶ ACTIVE ──被新条目替代──▶ SUPERSEDED（写明替代 ID 与版本，编号不复用）
```

"是否已验证"不是需求状态，而由 [07](07-verification-traceability.md) 的验收状态派生：
未覆盖 / 单测 / E2E / 单测+E2E / 仅手工。

### 6.2 来源标记

- `STD:x.y`：JTG/T 5230—2026 正式稿条款（含表 3.2.6、表 3.3.2、附录 B.1.3、附录 C）；
- `DEC:yyyy-mm-dd` 或 `DEC:v1.x`：用户/会议裁决或随指南版本确定的工程决策；
- `CODE`：无条款出处的现状契约（锚点即权威）；
- `GUIDE:`：仅交叉引用，不作为权威来源。

### 6.3 EARS 句式

| 句式 | 模板 | 本集实例 |
|---|---|---|
| 普遍式 | 系统应… | REQ-EVAL-801（统一舍入） |
| 事件式 | 当 \<触发\> 时，系统应… | REQ-SYNC-503（同步成功后重打包） |
| 状态式 | 当对象处于 \<状态\> 时，…应… | REQ-EVAL-604（B08–B21 拒绝） |
| 可选式 | 在 \<特性开启\> 时，系统应… | REQ-ATT-104（缩略图开关） |
| 非预期式 | 如果 \<异常\>，系统应…（而非…） | REQ-SYNC-104（逐行容错） |

### 6.4 条目模板

完整版（P0/含公式条目，10–14 行）：状态/优先级/来源/EARS → 需求陈述 → 编号验收准则
（GIVEN/WHEN/THEN 或可判定等式）→ 代码锚点 → 测试锚点 → 关联条目 → 变更记录。
紧凑版（P1/P2，4–6 行）：头部一行 + 一句话陈述 + 一条可判定验收 + 锚点。
天然成组的规则（删除语义、值域、端点族）合并为一条，组内用表格。

## 7. 记号约定

- 条款号写法：`STD:3.3.3`、`STD:表3.3.2`、`STD:B.1.3`；
- 代码锚点：`模块相对路径:行号（类名#方法名）`，行号对当前基线负责，符号对长期负责；
- YAML 锚点：`路径:键路径`（如 `meta.yml:bridgeTypes[B08].structureComplete`），不用行号；
- 小数：规格中出现的评分期望值（85.3、70.0、72.5…）均为 BigDecimal HALF_UP 后的值；
- “应/必须”用于 P0/P1 需求；“宜”用于 P2。
