# 02 5230 评定引擎规格

- 文档版本：v1.0 ｜ 最后更新：2026-10-30 ｜ 基线 commit：`843c0626`
- 代码根包：`bi-biz/src/main/java/edu/whut/cs/bi/biz/service/std/evaluation/`（下文锚点省略该前缀）
- 权威目录：`bi-biz/src/main/resources/std/5230-2026/`（下文记为 `std/5230-2026/`）

## 1. calculate 十二步装配流水线

入口 `Assess5230CalculateService#calculate(taskId, loginName)`（`Assess5230CalculateService.java:180`，
`@Transactional`），步骤与代码一一对应：

| 步 | 内容 | 代码行 |
|---|---|---|
| 1 | 任务校验（5230-2026）+ 根上下文（new_root_object_id）+ 结构解析 | :181-185；`loadContext` :624；`require5230Task` :639 |
| 2 | 锚定装载（bi_span_element_part），三元组与越界硬校验，构件装载 | :187-227 |
| 3 | 应设未设标记应用（已有 PART 置 absent/CI=3 或注入幻影件，必须在 B.1.3 前） | :229-236；`applyAbsentMark` :384 |
| 4 | 实际设置集合 presentSpanPartKeys（absent 部件下锚定不计） | :238-248 |
| 5 | 病害装载为 ESDI 标度（四连过滤） | :250-251；`loadStructuralScales` :438 |
| 6 | 人工判定三表装载（manual 按 elementUuid；单控按 hit=1） | :253-265 |
| 7 | 构件级评定（每条锚定一行；共用构件在所属每跨各一行） | :267-311 |
| 8 | 逐单元 B.1.3 权重重分配 | :313-317；`WeightRedistributor#applyB13` |
| 9 | 桥跨级评定（含 3.3.3 覆盖） | :319-328；`SpanEvaluator#evaluate` |
| 10 | 评定单元合成（3.4.1/3.4.2/3.4.3） | :330；`UnitEvaluator#evaluate` |
| 11 | 全桥聚合（3.4.4 最差单元 + 3.4.5 单控） | :333-334；`BridgeAggregator#aggregate` |
| 12 | 四张结果表按 task_id 先删后插 | :336-371 |

## 2. 权威目录与结构解析（REQ-EVAL-6xx）

#### REQ-EVAL-601 根的两种形态与确定性排序
- ACTIVE ｜ P0 ｜ 来源：STD:3.1.2 + CODE ｜ 普遍式
- 需求陈述：系统应支持两种根形态：①根节点本身为 UNIT（单评定单元）；②根为 ROOT，其直属子节点
  中多个 UNIT（纵向不同结构形式/横向拼宽）。层级固定为 UNIT→SPAN→LAYER→PART；UNIT 按 id、
  SPAN 按 (spanNo, id)、LAYER/PART 按 (orderNum, id) 确定性排序；offline_deleted=1 节点排除。
- 验收准则：GIVEN E2E-APP-001 的 ROOT+2 UNIT+4 SPAN WHEN previewTree/calculate THEN units 按
  UNIT id 升序、跨按 spanNo 升序，unitCount=2/spanCount=4。
- 代码锚点：`UnitStructureResolver#resolveAll`（`UnitStructureResolver.java:72-114`）；SPAN 排序
  :176-177；LAYER/PART 排序 :186、:197；排除删除节点 `childrenOfType` :391-401
- 测试锚点：UnitStructureResolverTest（9 例）；E2E-APP-001

#### REQ-EVAL-602 partKey / ω / γ 三级解析
- ACTIVE ｜ P0 ｜ 来源：CODE + STD:附录B ｜ 普遍式
- 需求陈述：系统应按"实例节点 propsJson/props → 绑定模板对象 bi_template_object
  （nodeCode/omega/gamma）→ YAML 权威目录按名称兜底"三级解析部件键、部件权重与结构层影响系数；
- layerKey 缺省时按 deck、superstructure、substructure、ancillary 的层序兜底；每次目录兜底写
  非阻断 warning。
- 验收准则：GIVEN 仅含 propsJson `{"part":"slab"}`、ω/γ 缺省的实例链 WHEN 评定 THEN 解析得
  partKey=slab、ω=75、γ(superstructure)=0.40（B01 目录），warnings 含兜底记录。
- 代码锚点：`resolvePart` :208-256（partKey :212-229、ω :231-246）；`resolveLayerKey`
  :258-276；`resolveGamma` :278-292；`readProps` :308-313；`templateObjectOf` :294-305
- 测试锚点：UnitStructureResolverTest 兜底/缺 key 用例；E2E-5230-001、E2E-APP-001

#### REQ-EVAL-603 三级解析全失败硬错误
- ACTIVE ｜ P0 ｜ 来源：CODE ｜ 非预期式
- 需求陈述：如果 partKey、ω 或 γ 在三级来源中均无法得到（含层缺 γ），系统应抛 ServiceException
  禁止评定，而不是以默认权重计算。
- 验收准则：无 key 部件链调用 calculate 抛出含"无法解析 part key/权重 ω/影响系数 γ"的错误，
  四表无写入。
- 代码锚点：:226-229、:243-246、:191-194；`phantomPart` 缺 γ :140-144
- 测试锚点：UnitStructureResolverTest `resolveAll_unresolvablePartKey_throws`、
  `phantomPart_unknownKey_throws`

#### REQ-EVAL-604 B08–B21 与未定义桥型硬拒绝
- ACTIVE ｜ P0 ｜ 来源：CODE + DEC（批次范围）｜ 状态式
- 需求陈述：当评定单元 bridge_type 在权威目录中无模板，或其 `structureComplete=false`
  （B08–B21 共 14 型）时，系统应硬拒绝评定且不写任何结果表。
- 验收准则：B08 任务 calculate 抛出含桥型代码与"暂不支持/结构不完整"的 ServiceException；
  element/span/unit/bridge 四表均无该 task 行。
- 代码锚点：`loadTemplate` `UnitStructureResolver.java:328-340`；YAML
  `std/5230-2026/meta.yml:10-40`
- 测试锚点：UnitStructureResolverTest `resolveAll_structureIncomplete_throws`、
  `resolveAll_missingBridgeType_throws`；E2E-5230-004

#### REQ-EVAL-605 残缺结构硬错误集
- ACTIVE ｜ P0 ｜ 来源：CODE ｜ 非预期式
- 需求陈述：如果出现以下任一情况，系统应硬错误：根节点为空/不存在；根下无 UNIT；UNIT 缺
  bridge_type；UNIT 下无 SPAN；任务未关联 building；building 缺 new_root_object_id。
- 验收准则：每种残缺结构各自抛出含实体 id 的 ServiceException。
- 代码锚点：:73-75、:78-91、:102-105、:163-165、:173-175；`loadContext` :624-637
- 测试锚点：UnitStructureResolverTest `resolveAll_rootWithoutUnits_throws`、
  `resolveAll_missingBridgeType_throws`

#### REQ-EVAL-606 应设未设幻影件构造
- ACTIVE ｜ P0 ｜ 来源：STD:3.2.2-5 ｜ 事件式
- 需求陈述：当 absent 标记的 partKey 在该跨无对应 PART 节点时，系统应按桥型目录构造幻影部件：
  partId 恒为 null、partKey/layerKey 取解析值、ω/γ 取目录、absent=true、CI 预置 3；partKey 不
  存在或层缺 γ 时硬错误。
- 验收准则：E2E-5230-001 的 bearing 幻影行 part_id/element_uuid 为 null、EDI=ECI=ECL=3、
  source=ABSENT。
- 代码锚点：`phantomPart` `UnitStructureResolver.java:123-156`；注入点
  `Assess5230CalculateService.java:419-425`
- 测试锚点：UnitStructureResolverTest `phantomPart_knownKey_buildsAbsentPartFromCatalog`；
  E2E-5230-001、E2E-APP-001

#### REQ-EVAL-607 YAML 权威目录内容
- ACTIVE ｜ P1 ｜ 来源：CODE ｜ 普遍式
- 需求陈述：系统应以 classpath `std/5230-2026/**/*.yml` 为结构与病害字典权威：meta.yml 21 桥型
  （B01–B07 complete）；templates/B01–B07.yml 给出四层 γ、上部部件 ω 与 leaves 双绑；
  shared-layers.yml 给出桥面系/下部/附属部件 ω；rules/main-parts.yml 给出主要部件清单；
  disease-types/*.yml 全部为 EDI 结构性缺损类型（1–3 标度）。
- 验收准则：StandardCatalog 启动加载成功（单例）；B01 γ={0.15,0.40,0.40,0.05}、上部 ω
  slab=75/bearing=25；B07 steelMainGirder/deckSlab 双叶子绑定可解析。
- 代码锚点：`service/std/StandardCatalogLoader.java`（`getCatalog` 单例、RESOURCE_PATTERN）；
  YAML 目录 `std/5230-2026/templates/B01.yml`、`B07.yml`、`rules/main-parts.yml`
- 测试锚点：StandardCatalogIntegrityTest；UnitStructureResolverTest

## 3. 构件级评定（REQ-EVAL-1xx，STD 3.2）

代码：`ComponentEvaluator#evaluate`（`ComponentEvaluator.java:35-97`）。

#### REQ-EVAL-101 ESDI 自动取值与四连过滤
- ACTIVE ｜ P0 ｜ 来源：STD:3.2 ｜ 普遍式
- 需求陈述：系统应将构件 ESDI 取为其参与评定病害标度的最大值并封顶 3；一条病害计入 ESDI 必须
  同时满足：① bi_disease.participate_assess='1'；② disease.element_uuid 能锚定到评定构件；
  ③ 病害类型 std_version='5230-2026'；④ 类型 scale_type='EDI'。未锚定（dangling）或类型不符
  （ignored）只计数进 warnings，不计标度。
- 验收准则：GIVEN EDI 标度 2 病害一条 + 不参与/未锚定/旧标类型各一条 WHEN 评定 THEN ESDI=2，
  warnings 含 dangling/ignored 计数。
- 代码锚点：`maxStructuralScale` `ComponentEvaluator.java:99-110`；过滤
  `Assess5230CalculateService#loadStructuralScales` :471、:475、:480-481、:487-492
- 测试锚点：ComponentEvaluatorTest `esdiTakesMaxStructuralScaleCappedAt3`；Assess5230CalculationE2ETest

#### REQ-EVAL-102 ESDI 人工覆盖优先（3.2.3）
- ACTIVE ｜ P0 ｜ 来源：STD:3.2.3 ｜ 事件式
- 需求陈述：当 bi_assess_manual_input.esdi_override 非空时，系统应以其值（0–3）覆盖自动 max，
  并将 esdi_source 留痕为 MANUAL；否则 AUTO。
- 验收准则：同构件病害 max=1、override=3 时 ESDI=3/MANUAL；无 override 时 ESDI=1/AUTO。
- 代码锚点：`ComponentEvaluator.java:60-68`
- 测试锚点：ComponentEvaluatorTest `manualEsdiOverrideBeatsDiseaseMax`

#### REQ-EVAL-103 EDDI 人工录入
- ACTIVE ｜ P1 ｜ 来源：STD:第4章（自动判定本期不做，REQ-BIZ-901）｜ 普遍式
- 需求陈述：EDDI 取人工录入值（0–2），未录入缺省 0；来源按是否录入标 MANUAL/AUTO。
- 代码锚点：`ComponentEvaluator.java:70-74`
- 测试锚点：ComponentEvaluatorTest `noDiseaseNoManualInput_allZeroEcl1`（全零 ECL1）

#### REQ-EVAL-104 EDI＝max(EDDI, ESDI)
- ACTIVE ｜ P0 ｜ STD:3.2 ｜ 普遍式：EDI = max(EDDI, ESDI)。
- 代码锚点：`ComponentEvaluator.java:76`
- 测试锚点：ComponentEvaluatorTest `ediIsMaxOfEddiAndEsdi`

#### REQ-EVAL-105 EFI 人工录入
- ACTIVE ｜ P1 ｜ 来源：STD:第14章（REQ-BIZ-902）｜ 普遍式：EFI 取人工录入值（0–2），缺省 0。
- 代码锚点：`ComponentEvaluator.java:78-79`
- 测试锚点：ComponentEvaluatorTest

#### REQ-EVAL-106 EAI 仅主要部件可取非 0
- ACTIVE ｜ P0 ｜ 来源：STD:3.2.5 ｜ 状态式
- 需求陈述：当构件所属部件在 rules/main-parts.yml 的主要部件清单内时，EAI 可取 -1/0/1；非主要
  部件录入非 0 时强制归 0 并写 warning。
- 验收准则：非主要部件 EAI=1 → EAI=0 + warning；主要部件 EAI=-1 保留。
- 代码锚点：`ComponentEvaluator.java:81-90`；主要件集合 `Assess5230CalculateService#mainPartKeys`
  :496-499；YAML `std/5230-2026/rules/main-parts.yml`
- 测试锚点：ComponentEvaluatorTest `mainPartAcceptsNegativeEai`、
  `nonMainPartEaiForcedToZeroWithWarning`

#### REQ-EVAL-107 ECI 截断
- ACTIVE ｜ P0 ｜ STD:3.2 ｜ 普遍式：ECI = clamp(EDI + EFI + EAI, 0, 5)。
- 代码锚点：`ComponentEvaluator.java:93`；`EvaluationMath#clampEci` `EvaluationMath.java:27-29`
- 测试锚点：ComponentEvaluatorTest `eciClampedAt5`

#### REQ-EVAL-108 ECL 映射（表 3.2.6）
- ACTIVE ｜ P0 ｜ 来源：STD:表3.2.6 ｜ 普遍式
- 需求陈述：ECL 按 ECI 映射：0/1→1 类，2→2，3→3，4→4，5→5（即 0 与 1 同归 1 类）。
- 代码锚点：`ComponentEvaluator#toEcl` `ComponentEvaluator.java:112-118`
- 测试锚点：ComponentEvaluatorTest `eclMapping_eci1to4`

#### REQ-EVAL-109 幻影件指数固定
- ACTIVE ｜ P0 ｜ 来源：STD:3.2.2-5 ｜ 状态式
- 需求陈述：幻影（应设未设）构件 EDDI=ESDI=EFI=EAI=0、EDI=3、ECI=3、ECL=3，两来源字段均为 ABSENT。
- 代码锚点：`ComponentEvaluator.java:50-58`；落库行 `Assess5230CalculateService#toAbsentElementRow`
  :529-555
- 测试锚点：ComponentEvaluatorTest `absentPhantomComponent_ediFixedAt3`；E2E-5230-001

#### REQ-EVAL-110 共用构件逐跨产行
- ACTIVE ｜ P1 ｜ 来源：CODE ｜ 普遍式
- 需求陈述：同一 bi_element 经锚定属于多个（跨×部件）时（is_shared=1），系统应在所属每跨各产生
  一条构件评定结果。
- 验收准则：一构件锚两行 → element_result 两行，span_id 不同，指数一致。
- 代码锚点：`Assess5230CalculateService.java:267-311`（按 span×anchor 循环）；
  is_shared 透传 :296、行映射 :514

## 4. 附录 B.1.3 权重重分配（REQ-EVAL-3xx）

代码：`WeightRedistributor#applyB13`（`WeightRedistributor.java:41-148`），逐单元执行、全桥各跨
统一 ω′、γ 不变。部件三分类以全桥各跨实际设置为准：

#### REQ-EVAL-301 全桥无须设置件同层比例并入
- ACTIVE ｜ P0 ｜ 来源：STD:B.1.3 ｜ 状态式
- 需求陈述：当部件在单元内全部跨均未设置且无 absent 标记时，判为"全桥无须设置"，其 ω 在同一
  结构层内按比例并入保留部件：**ω′(j) = ω(j) × 100 / Σ_{k∈R} ω(k)**，4 位小数 HALF_UP；无须
  设置件自身 ω′=0；γ 不变；ω′ 全桥各跨统一。
- 验收准则：B01 单跨仅设 slab、bearing 幻影（absent 保留）场景下 slab ω′=100（见 E2E-5230-001
  手算注）；纯无须设置件（无 absent）场景见 WeightRedistributorTest baselineA。
- 代码锚点：分类 :61-68；公式 :124-128；置零 :131-135；层分组 :70-77
- 测试锚点：WeightRedistributorTest `baselineARedistributesWhenPartUnnecessaryInAllSpans`、
  `gammaUntouchedAndWeightsUniformAcrossSpans`

#### REQ-EVAL-302 部分跨设置不参与再分配
- ACTIVE ｜ P0 ｜ STD:B.1.3 ｜ 状态式：部件仅部分跨设置时，不参与再分配，缺失跨该部件 CI=0，
  各跨统一按 ω′ 计。
- 代码锚点：presentSpans 收集 :51-58；CI=0 项不求和 `SpanEvaluator.java:54-57`
- 测试锚点：WeightRedistributorTest `noRedistributionWhenPartOnlyPartiallyMissing`

#### REQ-EVAL-303 absent 部件保留原始权重
- ACTIVE ｜ P0 ｜ STD:B.1.3 ｜ 普遍式：经标记的应设未设部件保留原始 ω、不进保留集 R 的求和分母、
  不参与再分配。
- 代码锚点：`WeightRedistributor.java:91-98、:121-123`
- 测试锚点：WeightRedistributorTest `absentPartKeepsWeightAndStaysOutOfR`、
  `keepsWeightsWhenLayerOnlyHasAbsentParts`

#### REQ-EVAL-304 保留集为空的错误与仅剩 absent 件
- ACTIVE ｜ P0 ｜ STD:B.1.3 + CODE ｜ 非预期式
- 需求陈述：如果某结构层 Σ_R ω = 0 且层内无 absent 件（保留集合为空），系统应硬错误；层内仅剩
  absent 件时其 ω 原样保留、不再分配并写 warning。
- 代码锚点：`WeightRedistributor.java:100-115`
- 测试锚点：WeightRedistributorTest `throwsWhenREmptyWithoutAbsentMark`

## 5. 桥跨级评定（REQ-EVAL-2xx，STD 3.3）

代码：`SpanEvaluator#evaluate`（`SpanEvaluator.java:37-101`）。

#### REQ-EVAL-201 部件 CI 取构件 ECI 最大值
- ACTIVE ｜ P0 ｜ STD:3.3.1 ｜ 普遍式
- 需求陈述：CI(j) = 部件 j 内全部真实构件 ECI 的最大值；幻影部件 CI=3 由装配阶段预置并跳过聚合；
  真实部件下无构件时 CI=0。
- 代码锚点：`SpanEvaluator.java:38-50`
- 测试锚点：SpanEvaluatorTest `absentPhantomPartUsesPresetCi3`、`noDiseaseSpan_bsci100Level1`

#### REQ-EVAL-202 BSCI 加权扣分公式
- ACTIVE ｜ P0 ｜ STD:3.3.1 ｜ 普遍式
- 需求陈述：系统应对每跨计算 **BSCI = 100 − Σ_j CI(j)·ω′(j)·γ(j)/5**；CI=0 的部件项不求和；
  逐项除法保留 6 位小数 HALF_UP，BSCI 结果保留 1 位。
- 验收准则：
  1. goldenC082：Σ CI·ω′·γ/5 = 14.7 → **BSCI=85.3、BSCL=2**（官方附录 C 算例 C.0.8-2）；
  2. E2E-5230-001：100 − 3×100×0.40/5 − 3×25×0.40/5 = **70.0**；
  3. 无病害空跨 BSCI=100.0。
- 代码锚点：`SpanEvaluator.java:52-64`
- 测试锚点：SpanEvaluatorTest#goldenC082_singleSpanBsci853_level2；E2E-5230-001；E2E-APP-001

#### REQ-EVAL-203 BSCL 分档（表 3.3.2，含边界归属）
- ACTIVE ｜ P0 ｜ STD:表3.3.2 ｜ 普遍式
- 需求陈述：BSCL 按 (90,100]→1、(75,90]→2、(60,75]→3、(40,60]→4、[0,40]→5 分档；**90.0 属
  2 类、40.0 属 5 类**。
- 代码锚点：`SpanEvaluator#bsclBand` `SpanEvaluator.java:103-121`
- 测试锚点：SpanEvaluatorTest `bsclBandBoundaries`

#### REQ-EVAL-204 3.3.3 安全影响覆盖
- ACTIVE ｜ P0 ｜ STD:3.3.3 ｜ 事件式
- 需求陈述：当构件同时满足"非幻影 + ECL∈{4,5} + 所属为主要部件 + 人工标记 safetyAffected=1"时，
  系统应取最差触发构件执行覆盖：**BSCI = min(公式原值, 档上限)**（ECL5→40，ECL4→60），
  **BSCL = max(原等级, 构件等级)**；公式原值/原等级/覆盖标志/覆盖等级/触发构件五列冻结留痕。
- 验收准则：EDI2+EFI2 → ECI4/ECL4 + safety=1：bsciRaw=68.0/3 类被覆盖为 BSCI=60.0/4 类，
  override_flag=1、trigger 含"3.3.3"。
- 代码锚点：`SpanEvaluator.java:75-99`；落库 `Assess5230CalculateService#toSpanRow` :557-574
- 测试锚点：SpanEvaluatorTest `safetyOverrideEcl4CapsBsciAt60`、
  `safetyOverrideEcl5CapsBsciAt40`、`nonMainPartDoesNotTriggerOverride`；E2E-5230-002

## 6. 评定单元合成（REQ-EVAL-4xx，STD 3.4.1–3.4.3）

代码：`UnitEvaluator#evaluate`（`UnitEvaluator.java:43-101`）；最差跨按 (BSCI, spanId) 取最小。

#### REQ-EVAL-401 单跨 SINGLE 直通
- ACTIVE ｜ P0 ｜ STD:3.4.1 ｜ 状态式：单元仅一跨时 BCL/BCI 直接取该跨 BSCL/BSCI
（combineMode=SINGLE，bsciAvg 同值）。
- 代码锚点：`UnitEvaluator.java:67-72`
- 测试锚点：UnitEvaluatorTest#singleSpanPassesThrough；E2E-5230-001

#### REQ-EVAL-402 多跨同级 EQUAL 算术均值
- ACTIVE ｜ P0 ｜ STD:3.4.2 ｜ 状态式
- 需求陈述：多跨且各跨 BSCL 一致时，BCL 取该等级，**BCI = 各跨 BSCI 算术平均值**（均值 6 位
  小数运算、结果 1 位），combineMode=EQUAL。
- 验收准则：goldenC083 引桥 8 跨（4×84.4 + 4×84.6）→ **BCI=84.5、BCL=2**；E2E-APP-001
  单元 2 两空跨 → BCI=100.0/1 类。
- 代码锚点：`UnitEvaluator.java:73-78、:103-109`
- 测试锚点：UnitEvaluatorTest#goldenC083_approachBridgeEqualClassMeanBci845；E2E-APP-001

#### REQ-EVAL-403 多跨异级 INTERP 插值
- ACTIVE ｜ P0 ｜ STD:3.4.3 ｜ 状态式
- 需求陈述：多跨且等级不一致时，BCL 取最差等级，最差等级档上限 lim 取 2 类 90 / 3 类 75 /
  4 类 60 / 5 类 40，并按
  **BCI = BSCI_min + (lim − BSCI_min) × (BSCI_avg − BSCI_min) / (BSCI_max − BSCI_min)**
  插值（除法 6 位 HALF_UP、结果 1 位）；min/max/avg/lim/worstSpanId 全部留痕。
- 验收准则：
  1. goldenC083 主桥 66.7(3)/87.6(2)/73.2(3)，avg=75.8 → **BCI=70.3、BCL=3**；
  2. E2E-APP-001 单元 1：70(3)/100(1)，avg=85.0、lim=75 → 70+(75−70)(85−70)/(100−70)
     = **72.5、BCL=3**。
- 代码锚点：`UnitEvaluator.java:79-99`；lim `bsciLimit` :111-125
- 测试锚点：UnitEvaluatorTest#goldenC083_mainBridgeInterpolation_bci703Level3；E2E-APP-001

#### REQ-EVAL-404 单元异常输入硬错误
- ACTIVE ｜ P0 ｜ CODE ｜ 非预期式：单元无桥跨结果，或 INTERP 分母为 0（各跨 BSCI 同值却等级
不一致，违反互斥分档，属数据异常）时，系统应抛 ServiceException。
- 代码锚点：`UnitEvaluator.java:44-46、:88-92、:122-124`
- 测试锚点：UnitEvaluatorTest `emptySpansThrow`、`interpZeroDenominatorThrows`

## 7. 全桥聚合（REQ-EVAL-5xx，STD 3.4.4/3.4.5）

代码：`BridgeAggregator#aggregate`（`BridgeAggregator.java:41-90`）。

#### REQ-EVAL-501 单单元 SINGLE_UNIT 直通
- ACTIVE ｜ P0 ｜ STD:3.4.4 ｜ 状态式：仅一个评定单元时全桥结果直通该单元（combineMode=SINGLE_UNIT）。
- 代码锚点：`BridgeAggregator.java:49-51`
- 测试锚点：BridgeAggregatorTest#singleUnitPassesThrough；E2E-5230-001

#### REQ-EVAL-502 多单元 WORST_UNIT
- ACTIVE ｜ P0 ｜ STD:3.4.4 ｜ 状态式
- 需求陈述：多评定单元时 combineMode=WORST_UNIT，按"BCL 最差（数值最大）→ 同级 BCI 最低 →
  再同级 unitId 较小"三级决胜取最差单元；其 BCL/BCI 同时作为分层结果 layeredBcl/layeredBci。
- 验收准则：E2E-APP-001：单元1 72.5/3、单元2 100/1 → 全桥 72.5/3、worstUnitId=单元1；
  goldenC083 主桥+引桥组合取 70.3/3。
- 代码锚点：`BridgeAggregator.java:52-63`
- 测试锚点：BridgeAggregatorTest `worstUnitByLevel_344`、`sameWorstLevelTakesLowerBci`；
  E2E-APP-001

#### REQ-EVAL-503 3.4.5 十六项单项控制覆盖
- ACTIVE ｜ P0 ｜ STD:3.4.5 ｜ 事件式
- 需求陈述：当 bi_assess_single_control_mark 中任一 hit=1 时（编号去重升序），系统应将全桥
  **BCL 定为 5、BCI = min(layeredBci, 40)**；layeredBci/layeredBcl 保留分层原值；命中编号以
  逗号拼接（如 "3,7"）存入 single_control_hits；未命中时 hits 为 null、全桥取分层结果。
- 验收准则：E2E-5230-003：在 70.0/3 链上命中指标 7 → BCL=5、BCI=40.0、layeredBcl=3、
  layeredBci=70.0、hits="7"。
- 代码锚点：`BridgeAggregator.java:65-88`；hit 装载 `Assess5230CalculateService.java:260-265`；
  枚举 `domain/enums/SingleIndicator.java:10-25`
- 测试锚点：BridgeAggregatorTest `singleControlHitForcesClass5AndCapsAt40`、
  `singleControlKeepsBciWhenLayeredAlreadyBelow40`；Assess5230CalculateServiceTest；E2E-5230-003

#### REQ-EVAL-504 非法指标编号防御
- ACTIVE ｜ P1 ｜ CODE ｜ 非预期式：indicatorNo 不在 1–16 时
`SingleIndicator.valueOfNo` 抛 IllegalArgumentException（同步行校验另见 REQ-SYNC-401）。
- 代码锚点：`SingleIndicator#valueOfNo` `SingleIndicator.java:50-57`；调用点
  `BridgeAggregator.java:70`
- 测试锚点：BridgeAggregatorTest `invalidIndicatorNoThrows`；AssessInputRowValidatorTest
  `singleControl_indicatorRangeOneToSixteen`

## 8. 人工判定输入与逐行校验（REQ-EVAL-701/702）

代码：`AssessInputRowValidator`（`AssessInputRowValidator.java`）；三表仅由 sync 管线写入
（REQ-BIZ-104，管线段规则见 03 篇 REQ-SYNC-401~405）。

#### REQ-EVAL-701 三表行值域校验
- ACTIVE ｜ P0 ｜ STD + CODE ｜ 普遍式
- 需求陈述：系统应对三表逐行做值域校验，返回错误列表（不抛异常、默认值原地回填）；任一字段
  非法的行在同步时不得落库。值域：

| 表/字段 | 规则 |
|---|---|
| manual：taskId / elementUuid | 必填 |
| eddi / esdiOverride / efi | null 或 0–2 / 0–3 / 0–2 |
| eai | null 或 -1–1 |
| safetyAffected | 缺省 0，仅允许 0/1 |
| absent：taskId / partKey | 必填；unitUuid/spanUuid 可空（广播） |
| absent：layerKey | 空，或 deck/superstructure/substructure/ancillary 之一 |
| singleControl：indicatorNo | 必填且 1–16；hit 缺省 1，仅 0/1 |
| 三表 evidence | 超 500 字截断并附告警 |

- 代码锚点：`validateManualInput` :25-44、`validateAbsent` :47-61、
  `validateSingleControl` :64-85、`checkRange` :87-95、`truncate` :97-103
- 测试锚点：AssessInputRowValidatorTest 11 例

#### REQ-EVAL-702 absent 标记的广播、置位与幻影注入
- ACTIVE ｜ P0 ｜ STD:3.2.2-5 + CODE ｜ 事件式
- 需求陈述：应用 absent 标记时：unitId 空=全部评定单元，spanId 空=该单元全部跨（服务端 id 由
  UUID 反查回填）；作用范围零命中任何跨→硬错误；目标跨已有同 partKey 的 PART→置 absent=true、
  CI=3；无 PART→按 REQ-EVAL-606 注入幻影件；partKey 必填。
- 验收准则：E2E-APP-001 的 absent(u1,u1s1,bearing,superstructure) 恰好影响单元1第1跨，产生
  一条幻影 bearing 结果行，其余跨不受影响。
- 代码锚点：`applyAbsentMark` `Assess5230CalculateService.java:384-427`
- 测试锚点：E2E-5230-001、E2E-APP-001

## 9. 装配、落库与查询（REQ-EVAL-710~716）

#### REQ-EVAL-710 固定装配顺序
- ACTIVE ｜ P0 ｜ CODE ｜ 普遍式：系统应按第 1 节十二步固定顺序装配；B.1.3 必须在 absent 标记
与病害/人工装载之后、桥跨评定之前执行。
- 代码锚点：`Assess5230CalculateService.java:184-334`
- 测试锚点：Assess5230CalculateServiceTest 2 例；全部 E2E-5230/APP

#### REQ-EVAL-711 非 5230 任务硬拒绝
- ACTIVE ｜ P0 ｜ CODE ｜ 状态式：taskId 为空/任务不存在/std_version≠'5230-2026'、任务无
building、building 缺 new_root_object_id 时，五个查询/计算端点共用同一拒绝逻辑。
- 代码锚点：`require5230Task` :639-651；`loadContext` :624-637；HTTP 投影见 REQ-API-305
- 测试锚点：E2E-5230-004（错误信封）

#### REQ-EVAL-712 锚定三元组硬校验
- ACTIVE ｜ P0 ｜ CODE ｜ 非预期式
- 需求陈述：如果锚定记录缺 element_id/span_id/part_id 任一、span_id 不在解析结构内、part_id
不是结构内部件、或锚定构件在 bi_element 中不存在/已删，系统应抛 ServiceException 禁止评定。
- 代码锚点：`Assess5230CalculateService.java:206-221、:280-283`
- 测试锚点：NewChainSyncUploadE2ETest（缺锚定拒绝，管线侧）；E2E-NEW-003

#### REQ-EVAL-713 结果四表幂等重算
- ACTIVE ｜ P0 ｜ CODE ｜ 事件式：每次 calculate 应按 task_id 先删后插四张结果表
（element_result、bi_span_evaluation[含 unit_id]、unit_result、bridge_result），createBy 取
登录名或 "system"；重复计算行数不翻倍。
- 代码锚点：删除 :338-341；插入 :343-371
- 测试锚点：E2E-5230-001 幂等断言；E2E-APP-001 重算断言；Assess5230CalculateServiceTest

#### REQ-EVAL-714 幻影结果行与四层结果查询
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式：幻影件应落 absent_flag=1 的 element_result 行（partId 为空、
EDI/ECI/ECL=3、来源 ABSENT）；getResult 返回最近一次
{bridge, units, spans, elements} 四层结果。
- 代码锚点：`toAbsentElementRow` :529-555；`getResult` :162-170
- 测试锚点：E2E-5230-001/APP-001（element 行数与字段）

#### REQ-EVAL-715 非阻断告警全量收集
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式：系统应收集并随 calculate/tree 响应返回全部非阻断告警，至少
包括：非主要部件 EAI 归零、三级解析目录兜底、全桥无须设置、幻影注入、dangling/ignored 病害
计数、absent 部件下锚定忽略、层内仅剩 absent 件。
- 代码锚点：`EvalWarnings`（`EvalWarnings.java`）；各 warnings.add 点（见上述条目）；
  响应装配 :375、:147
- 测试锚点：ComponentEvaluatorTest/WeightRedistributorTest/UnitStructureResolverTest 中断言
  warning 的用例

#### REQ-EVAL-716 tree/inputs/result 只读
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式：previewTree 只做结构解析不落任何数据；getInputs/getResult
为只读回查。
- 代码锚点：`previewTree` :140-149；`getInputs` :152-159；`getResult` :162-170
- 测试锚点：E2E-5230-001（tree 预览后库表无行）

## 10. 数值规则横切（REQ-EVAL-801）

#### REQ-EVAL-801 统一舍入与 BigDecimal
- ACTIVE ｜ P0 ｜ STD + CODE ｜ 普遍式
- 需求陈述：全部评定指数结果统一保留 1 位小数 HALF_UP；BSCI 逐项除法、INTERP 插值与 EQUAL
  均值的中间过程保留 6 位小数；B.1.3 的 ω′ 保留 4 位；全程使用 BigDecimal，禁止经
  `new BigDecimal(double)` 转换。
- 验收准则：golden 三算例（85.3/70.3/84.5）与现场链（70.0/72.5）精确成立；代码中无 double
  直转构造 BigDecimal。
- 代码锚点：`EvaluationMath#scale1` `EvaluationMath.java:14-22`；6 位除法
  `SpanEvaluator.java:61`、`UnitEvaluator.java:95、:108`；4 位 `WeightRedistributor.java:32、:127`
- 测试锚点：全部 golden 用例

## 篇末追溯矩阵

| 需求 ID | 代码符号锚点 | 主要测试锚点 | 验收状态 |
|---|---|---|---|
| REQ-EVAL-601 | UnitStructureResolver#resolveAll | UnitStructureResolverTest、E2E-APP-001 | 单测+E2E |
| REQ-EVAL-602 | #resolvePart/#resolveLayerKey/#resolveGamma | Resolver 9 例、E2E-5230-001 | 单测+E2E |
| REQ-EVAL-603 | :226-246 / :191-194 | unresolvable*_throws | 单测 |
| REQ-EVAL-604 | #loadTemplate | resolveAll_structureIncomplete_throws、E2E-5230-004 | 单测+E2E |
| REQ-EVAL-605 | resolveAll/resolveUnit/loadContext | resolveAll_rootWithoutUnits_throws 等 | 单测 |
| REQ-EVAL-606 | #phantomPart、:419-425 | phantomPart_*、E2E-5230-001 | 单测+E2E |
| REQ-EVAL-607 | StandardCatalogLoader、std/5230-2026/*.yml | StandardCatalogIntegrityTest | 单测 |
| REQ-EVAL-101 | #loadStructuralScales、#maxStructuralScale | esdiTakesMax*、E2E-5230-001 | 单测+E2E |
| REQ-EVAL-102 | ComponentEvaluator:60-68 | manualEsdiOverrideBeatsDiseaseMax | 单测 |
| REQ-EVAL-103 | ComponentEvaluator:70-74 | noDiseaseNoManualInput_allZeroEcl1 | 单测 |
| REQ-EVAL-104 | ComponentEvaluator:76 | ediIsMaxOfEddiAndEsdi | 单测 |
| REQ-EVAL-105 | ComponentEvaluator:78-79 | ComponentEvaluatorTest | 单测 |
| REQ-EVAL-106 | ComponentEvaluator:81-90、main-parts.yml | mainPartAcceptsNegativeEai 等 | 单测 |
| REQ-EVAL-107 | EvaluationMath#clampEci | eciClampedAt5 | 单测 |
| REQ-EVAL-108 | #toEcl | eclMapping_eci1to4 | 单测 |
| REQ-EVAL-109 | ComponentEvaluator:50-58、#toAbsentElementRow | absentPhantom*、E2E-5230-001 | 单测+E2E |
| REQ-EVAL-110 | CalculateService:267-311 | E2E（共用构件场景） | E2E |
| REQ-EVAL-301 | WeightRedistributor:124-135 | baselineA、gammaUntouched | 单测 |
| REQ-EVAL-302 | WeightRedistributor:51-58；Span:54-57 | noRedistributionWhenPartOnlyPartiallyMissing | 单测 |
| REQ-EVAL-303 | WeightRedistributor:91-123 | absentPartKeepsWeight* 等 | 单测 |
| REQ-EVAL-304 | WeightRedistributor:100-115 | throwsWhenREmpty*、keepsWeights* | 单测 |
| REQ-EVAL-201 | SpanEvaluator:38-50 | absentPhantomPart*、noDiseaseSpan | 单测 |
| REQ-EVAL-202 | SpanEvaluator:52-64 | **goldenC082=85.3**、E2E-5230-001=70.0 | 单测+E2E |
| REQ-EVAL-203 | #bsclBand | bsclBandBoundaries（含 90/40 边界） | 单测 |
| REQ-EVAL-204 | SpanEvaluator:75-99 | safetyOverride*、E2E-5230-002 | 单测+E2E |
| REQ-EVAL-401 | UnitEvaluator:67-72 | singleSpanPassesThrough、E2E-5230-001 | 单测+E2E |
| REQ-EVAL-402 | UnitEvaluator:73-78、:103-109 | **goldenC083 引桥=84.5**、E2E-APP-001 | 单测+E2E |
| REQ-EVAL-403 | UnitEvaluator:79-99、:111-125 | **goldenC083 主桥=70.3**、E2E-APP-001=72.5 | 单测+E2E |
| REQ-EVAL-404 | UnitEvaluator:44-46、:88-92 | emptySpansThrow、interpZeroDenominator | 单测 |
| REQ-EVAL-501 | BridgeAggregator:49-51 | singleUnitPassesThrough | 单测+E2E |
| REQ-EVAL-502 | BridgeAggregator:52-63 | worstUnit*、E2E-APP-001 | 单测+E2E |
| REQ-EVAL-503 | BridgeAggregator:65-88、SingleIndicator | singleControl*、E2E-5230-003 | 单测+E2E |
| REQ-EVAL-504 | SingleIndicator#valueOfNo | invalidIndicatorNoThrows、Validator | 单测 |
| REQ-EVAL-701 | AssessInputRowValidator 三方法 | AssessInputRowValidatorTest 11 例 | 单测 |
| REQ-EVAL-702 | #applyAbsentMark | E2E-5230-001、E2E-APP-001 | E2E |
| REQ-EVAL-710 | CalculateService:184-334 | CalculateServiceTest、全部引擎 E2E | 单测+E2E |
| REQ-EVAL-711 | #require5230Task/#loadContext | E2E-5230-004 | E2E |
| REQ-EVAL-712 | CalculateService:206-221、:280-283 | E2E-NEW-003 | E2E |
| REQ-EVAL-713 | CalculateService:338-371 | E2E-5230-001/APP-001 幂等 | E2E |
| REQ-EVAL-714 | #toAbsentElementRow、#getResult | E2E-5230-001/APP-001 | E2E |
| REQ-EVAL-715 | EvalWarnings + 各 add 点 | 多个单测 warning 断言 | 单测 |
| REQ-EVAL-716 | #previewTree/#getInputs/#getResult | E2E-5230-001 | E2E |
| REQ-EVAL-801 | EvaluationMath#scale1 等 | 全部 golden 用例 | 单测+E2E |
