# 07 验证与双向追溯

- 文档版本：v1.0 ｜ 最后更新：2026-10-30 ｜ 基线 commit：`843c0626`
- 本篇不含需求条目，是 01–06 全部 REQ 的验收物证索引。验收状态五档：
  未覆盖 / 单测 / E2E / 单测+E2E / 仅手工。

## 1. 验证策略与环境前置

### 1.1 三层策略

| 层 | 形态 | 依赖 | 覆盖对象 |
|---|---|---|---|
| L1 纯单测 | JUnit5，直接 new 被测类/ mapper mock | 无外部依赖 | 公式、分档、值域、解析（评定包 57 例 + 周边 54 例） |
| L2 E2E | `@SpringBootTest(classes=RuoYiApplication.class)` + `@Tag("E2E")`，真实 MySQL/MinIO/SQLite，部分类 MockMvc | docker MySQL 3307 + MinIO 9000 | 装配、落库、管线、HTTP 投影、现场全链（41 例，见 §4） |
| L3 手工抽检 | 真实 HTTP 脚本（仓库外）/ 接口工具 | 本地起服 8088 | 权限点、端到端真实 JWT/Shiro 链路 |

### 1.2 环境前置（与[后端数据同步开发指南 §6.2](../../doc/后端数据同步开发指南.md) 同源）

- docker `seedfix`：MySQL 端口 **3307**、库 `bi_test`，依次导入 bi_schema → ry →
  `sql/test_env_all_in_one.sql`（**跑两遍**），并已执行：
  - **第八·十部分**：`bi_file_upload_registry`（通道 A 前置，REQ-ATT-404）；
  - **第八·十一部分**：六张 `bi_assess_` 表 + `bi_span_evaluation.unit_id`
    + 3.3.3 五列（引擎前置）；
  - **第八·十二部分**：`DROP TABLE bi_span_component_part`（RE-SYNC-303 冻结段旁证）。
- DDL 预检三句：`SHOW TABLES LIKE 'bi_assess_%';` 返回 6 行；
  `SHOW COLUMNS FROM bi_span_evaluation LIKE 'unit_id';` 返回 1 行；
  `SHOW TABLES LIKE 'bi_span_component_part';` 返回空。
- docker `seedminio`：MinIO **9000**，bucket `public` 公开读。
- 账号：`znjc_demo/admin123`（user_id=100，5230 种子自带）；`znjc_test_1/123456`
  （user_id=101，**种子不含**，重建容器须从 znjc_demo 克隆 sys_user/sys_user_role，
  口令按 `Md5Hash(loginName+'123456'+salt)` 生成）。
- Maven 默认 `skip.admin.tests=true`（ruoyi-admin/pom.xml:19），E2E 必须显式关闭。
- 条件执行：SyncSqliteClosedLoopE2ETest（CL 系列）以
  `Assumptions.assumeTrue(isServerReachable())` 守卫，默认打 `http://localhost:80`，
  不可达时 5 例整体跳过（不计失败）。

## 2. Golden 算例表（改公式先改本表）

公式类需求（EVAL-1xx/2xx/3xx/4xx/5xx/801）的验收准则必须引用本表既有行或在同 PR 新增行。

| Golden | 标准出处 | 输入要点 | 期望值（HALF_UP） | 锁定需求 | 测试锚点 |
|---|---|---|---|---|---|
| goldenC082 | 附录 C 算例 C.0.8-2 | 单跨加权链，Σ CI·ω′·γ/5=14.7 | **BSCI=85.3、BSCL=2** | EVAL-202/203/801 | SpanEvaluatorTest#goldenC082_singleSpanBsci853_level2 |
| goldenC083-主桥 | 附录 C 算例 C.0.8-3 | 66.7(3)/87.6(2)/73.2(3)，avg=75.8，lim=75 | **BCI=70.3、BCL=3**（INTERP） | EVAL-403 | UnitEvaluatorTest#goldenC083_mainBridgeInterpolation_bci703Level3 |
| goldenC083-引桥 | 同上 | 8 跨 4×84.4+4×84.6 同级 | **BCI=84.5、BCL=2**（EQUAL） | EVAL-402 | UnitEvaluatorTest#goldenC083_approachBridgeEqualClassMeanBci845 |
| 3.4.4 最差单元 | 3.4.4 + C.0.8-3 | 主桥 70.3/3 + 引桥 84.5/2 | 全桥 70.3/3，worstUnit=主桥 | EVAL-502 | BridgeAggregatorTest `worstUnit*` |
| 3.3.3 覆盖 | 3.3.3 | EDI2+EFI2→ECI4/ECL4，主要部件，safety=1；raw=68.0/3 | **BSCI=60.0、BSCL=4**，raw/标志/等级/触发件五列冻结 | EVAL-204 | SpanEvaluatorTest `safetyOverride*`；E2E-5230-002 |
| 3.4.5 单控 | 3.4.5 | 70.0/3 链命中指标 7 | **BCL=5、BCI=40.0**，layered 70.0/3，hits="7" | EVAL-503 | BridgeAggregatorTest `singleControl*`；E2E-5230-003 |
| E2E-5230-001 | 3.2.2-5+3.3+3.4.1 | 单跨 slab + bearing 幻影：100−3×100×0.40/5−3×25×0.40/5 | **BSCI=70.0/3**，单元 SINGLE 70.0/3；重算幂等 | EVAL-109/202/303/401/713 | Assess5230CalculationE2ETest |
| E2E-APP-001 | 全链 | 单元1：损跨 70.0/3 + 空跨 100.0/1（avg 85.0，lim 75）；单元2：两空跨 | 单元1 **INTERP BCI=72.5/3**；单元2 **EQUAL BCI=100.0/1**；全桥 **WORST_UNIT 72.5/3** worstUnitId=u1；bi_building 零变更 | EVAL-402/403/502 + BIZ-102/103 | AppOfflineWorkflowE2ETest |
| E2E-5230-004 | 范围闸门 | B08 任务 | tree/calculate 均被拒绝，四表无行 | EVAL-604/711、API-105/305 | Assess5230CalculationE2ETest |

边界单点（单测锁定，非独立 golden）：BSCL 90.0→2 类、40.0→5 类（EVAL-203，
SpanEvaluatorTest#bsclBandBoundaries）；ECI 0/1→ECL 1 类（EVAL-108）；ECI clamp 5（EVAL-107）；
INTERP 分母 0 硬错误（EVAL-404）。

## 3. T1 标准条款 / 业务裁决 → 需求（正向索引）

| 条款 / 裁决 | 需求 ID | 覆盖判定 |
|---|---|---|
| STD:3.1.2（根两形态、层级） | EVAL-601 | 完整 |
| STD:3.2（构件级总链） | EVAL-101~110、701、801 | 完整 |
| STD:3.2.2-5（应设未设） | EVAL-109、606、702 | 完整 |
| STD:3.2.3（ESDI 人工覆盖） | EVAL-102 | 完整 |
| STD:3.2.5（EAI 主要部件限制） | EVAL-106 | 完整 |
| STD:表3.2.6（ECL 映射） | EVAL-108 | 完整 |
| STD:B.1.3（权重重分配） | EVAL-301~304 | 完整 |
| STD:3.3.1（CI、BSCI） | EVAL-201、202 | 完整 |
| STD:表3.3.2（BSCL 分档） | EVAL-203 | 完整 |
| STD:3.3.3（安全影响覆盖） | EVAL-204、BIZ-005 | 完整 |
| STD:3.4.1 / 3.4.2 / 3.4.3 | EVAL-401 / 402 / 403、404 | 完整 |
| STD:3.4.4（全桥最差单元） | EVAL-501、502 | 完整 |
| STD:3.4.5（16 项单项控制） | EVAL-503、504 | 完整 |
| STD:附录 C（算例/报告） | 算例→§2 Golden；报告导出→BIZ-904（不做） | 算例完整；导出排除 |
| STD 第 4 章（EDDI 自动判定） | 现状 EVAL-103（人工录入）；自动判定 BIZ-901（不做） | 按边界 |
| STD 第 14 章（EFI 相关） | 现状 EVAL-105（人工）；非 EDI 自动判定 BIZ-902（不做） | 按边界 |
| STD meta（B01–B07 complete，B08–B21 桩） | EVAL-604、607、BIZ-903 | 完整（含反向验收） |
| DEC:2026-10-27（云/现边界四裁决） | BIZ-101、102、103、104；SYNC-501、502 | E2E-APP-001 锁定 |
| DEC:v1.2（通道 A/挂接/重算） | ATT-101~404；SYNC-305、306 | 完整 |
| DEC:v1.4（十二段/三表单通道/指南版本） | BIZ-104；SYNC-101、401~405 | 完整 |
| DEC:v1.6（对象归属/双树/未知键） | SYNC-102、201~203；PKG-106 | 完整 |
| CODE 横切（事务、幂等、信封、裸 Map、WAL、对象名） | SYNC-103~106、308、504；PKG-202、203；API-303 | 完整 |

## 4. T2 测试 → 需求（反向索引）

### 4.1 单元测试

| 测试类 | @Test | 主要覆盖需求 |
|---|---|---|
| ComponentEvaluatorTest | 10 | EVAL-101~110 |
| SpanEvaluatorTest | 7 | EVAL-201~204（含 goldenC082、边界） |
| WeightRedistributorTest | 6 | EVAL-301~304 |
| UnitEvaluatorTest | 5 | EVAL-401~404（含 goldenC083 主/引桥） |
| BridgeAggregatorTest | 7 | EVAL-501~504 |
| UnitStructureResolverTest | 9 | EVAL-601~606、702 前置 |
| AssessInputRowValidatorTest | 11 | EVAL-701、504；SYNC-401 |
| Assess5230CalculateServiceTest | 2 | EVAL-710 装配序 |
| **评定包小计** | **57** | EVAL 全主体 |
| StandardCatalogIntegrityTest（service/std） | 11 | EVAL-607 |
| SyncUploadAssessMarksTest（bi-biz） | 10 | SYNC-401~405、BIZ-104 |
| SyncAttachmentServiceImplTest（bi-biz） | 14 | ATT-101~107、202、301、403 |
| SqliteServiceTest（bi-biz） | 9 | PKG-101~107、202、203、301/302 |
| ApiControllerV2Test（bi-api） | 21 | API-201~204、304；SYNC-102、503 |

### 4.2 E2E（@Tag("E2E")，计数以 @DisplayName 41 为准）

@DisplayName 41 = 用例数；@Test 注解 48，差额 7 为非用例辅助方法
（stubSqliteRegen/cleanup/建模助手等），不计验收用例。

| 规格 ID | 类中原名（@DisplayName） | 测试类 | disp/@Test |
|---|---|---|---|
| E2E-5230-001~004 | E2E-5230-001/002/003/004 | Assess5230CalculationE2ETest | 4 / 5 |
| E2E-APP-001 | E2E-APP-001（现场全链；真实 MinIO 预上传） | AppOfflineWorkflowE2ETest | 1 / 2 |
| E2E-NEW-001~004 | E2E-NEW-001/002/003/004 | NewChainSyncUploadE2ETest | 4 / 5 |
| E2E-ATT-001~005 | E2E-ATT-001~005 | AttachmentSyncUploadE2ETest | 5 / 6 |
| E2E-ATT-006~011 | E2E-ATT-006~011 | BatchAttachmentUploadE2ETest | 6 / 7 |
| E2E-HTTP-001~010（别名） | PostUpload-Test2/3/4/5/6/8/9/11/12/13 | PostSyncUploadE2ETest | 10 / 11 |
| E2E-SVC-001~006（别名） | E2E2-002/003/004/006/008/009 | SyncUploadE2ETest | 6 / 7 |
| E2E-CL-001~005（别名，**条件执行**） | ClosedLoop-02/03/04/05/07 | SyncSqliteClosedLoopE2ETest | 5 / 5 |
| **合计** | | | **41 / 48** |

别名规则：规格统一编号（HTTP/SVC/CL）只在本规格集与文档中使用，测试代码保留历史原名；
引用时两者必须成对出现（本表即对账簿）。CL 系列在 localhost:80 不可达时 assumption 跳过。

## 5. T3 缺口登记（未自动化的承诺与建议补测）

| 对象 | 现状 | 建议 |
|---|---|---|
| REQ-API-103/302 权限点（view/calculate 拒绝路径） | 仅手工：现有 E2E 均以有权限账号执行，无 403/无权限断言 | 增加无权限账号访问五端点的 L2/L3 用例 |
| REQ-API-301 双认证域交叉（无 token / 无会话） | 仅手工 | 与上条合并补测 |
| REQ-SYNC-303 components 冻结段 | 无新链覆盖（有意冻结，旧链用例已清理） | 不补测；行为变更时先解冻条目 |
| REQ-ATT-402 / REQ-API-205 通道 B | 无（暂停项） | 复活时补 E2E |
| REQ-ATT-404 DDL 前置 | 仅环境预检（§1.2 三句 SQL） | 保持手工；CI 化时做启动期 schema 断言 |
| REQ-PKG-201 列例外 | 间接覆盖（E2E-APP-001 消费样例库） | 生成器与服务建表列集做一次自动化 diff |
| REQ-EVAL-403 INTERP 二档边界 | 现有 lim=75（最差 3 类）；缺最差 2 类 lim=90 档位与"avg>90"组合 | 补 UnitEvaluatorTest lim=90 用例 |
| REQ-EVAL-106/107 组合 | 单项已覆盖；缺 clamp 触顶同时 EAI=−1 的组合断言 | 补 1 条组合单测 |
| REQ-EVAL-110 共用构件多跨行 | 仅 E2E 间接覆盖 | 补 CalculateService 层单测固定行数预期 |
| REQ-SYNC-102 未知顶层键/旧 spanComponentParts 静默忽略 | 无显式用例（fastjson + 具名读取隐式保护） | 补 1 条含未知键的 sync E2E/单测，断言已知数组正常落库 |

未列入缺口的"仅手工"条目（SYNC-303、ATT-402 等）状态原因见各篇篇末矩阵。

## 6. 手工验证命令

JDK17 固定（与 00 §4 一致）：

```bash
export JAVA_HOME=/Users/qixin/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home

# L1：bi-biz 全部纯单测（无需容器）
mvn -pl bi-biz -am test
# 单类示例
mvn -pl bi-biz -am -Dtest=SpanEvaluatorTest test
# bi-api 控制器单测
mvn -pl bi-api -am -Dtest=ApiControllerV2Test test

# L2：ruoyi-admin E2E（默认 skip.admin.tests=true，须显式打开；
#     反应器中单模块单跑必须带 failIfNoSpecifiedTests=false；
#     -Dtest 可替换为本篇 §4.2 的八个类名之一或逗号列表）
mvn -pl ruoyi-admin -am \
  -Dskip.admin.tests=false \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=Assess5230CalculationE2ETest,AppOfflineWorkflowE2ETest \
  -DargLine="-Dspring.datasource.druid.master.url=jdbc:mysql://localhost:3307/bi_test?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8 \
             -Dspring.datasource.druid.master.username=root \
             -Dspring.datasource.druid.master.password=root \
             -Dminio.endpoint=http://localhost:9000 \
             -Dminio.url=http://localhost:9000" \
  test
```

CL 系列另走真实 HTTP（80 端口可达时执行），系统属性可覆盖
`sync.baseUrl/sync.username/sync.password/sync.userId`
（SyncSqliteClosedLoopE2ETest.java:53-56；注意默认 userId=95，容器环境按 101 传入）。

踩坑：**`mvn spring-boot:run` 加载 ~/.m2 中旧 bi-biz jar**——改码后手工 HTTP 验证前必须先

```bash
mvn -pl bi-biz,bi-api -am install -DskipTests
```

再重启服务，否则可能 E2E 全过而 HTTP 仍是旧实现（后端指南 §6.3 记录过该事故）。
完整 HTTP 抽检脚本说明见后端指南 §6.3。

## 7. 文档自校验（季度对账执行）

1. **ID 唯一有定义**：01–06 中每个 `REQ-*-*` 在全篇恰好定义一次，07 只引用不定义；
   SUPERSEDED 占位不被新条目复用编号。
2. **引用闭合**：07 的 T1/T2 与各篇"关联条目/交叉引用"中出现的 ID 均有定义条目。
3. **锚点可 grep**：P0 条目 100%、P1 ≥20% 抽查 `文件:行号` 对应文件存在、符号可 grep、
   行号在文件长度内；YAML 锚点用 `路径:键`（不用行号）。
4. **E2E 对账**：§4.2 别名与测试类 @DisplayName 一一对应；41/48 两个口径数字与源码一致。
5. **矩阵一致**：各篇篇末四列矩阵的 ID 集合与本篇正文定义的 ID 集合严格相等。

锚点原则"符号优先、行号辅助"：纯重构只刷新行号与基线 commit（PATCH）；行为变更按
[README §7](README.md) 同 PR 更新条目、矩阵与本篇对应行。
