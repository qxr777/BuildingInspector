# 规格驱动开发（SDD）规格集 — JTG/T 5230—2026 落地子系统

- 文档版本：v1.0 ｜ 最后更新：2026-10-30 ｜ 基线 commit：`843c0626`
- 适用标准：JTG/T 5230—2026《公路桥梁技术状况评定标准》（2026-08-26 发布，2026-11-01 实施，替代 JTG/T H21—2011）
- 状态：ACTIVE（初版，描述基线 commit 时的已交付系统）

## 1. 这套文档回答什么

本规格集按 **规格驱动开发（Specification-Driven Development, SDD）** 组织，回答三个问题：

1. **系统应做什么**——以标准条款与业务裁决为唯一源头的需求条目（REQ）；
2. **怎样算做对了**——每条需求带 EARS 句式的可验证验收准则（GIVEN/WHEN/THEN、阈值、错误信封）；
3. **如何证明做对了**——标准条款 ↔ 需求 ↔ 代码符号 ↔ 测试用例双向追溯（见 [07](07-verification-traceability.md)）。

本规格集**不回答**"怎么实现/怎么对接"：载荷字段全表、SQLite DDL、curl/Vue 示例、时序图、FAQ
属于两份开发指南，见下节边界。

## 2. 与既有文档的关系与权威边界

| 维度 | 本规格集（docs/sdd） | [后端数据同步开发指南](../../doc/后端数据同步开发指南.md) v1.5 | [APP 离线同步开发指南](../../doc/APP%20离线同步开发指南.md) v1.6 |
|---|---|---|---|
| 核心问题 | 应做什么、验收标准、追溯 | 后端怎么实现、怎么运维 | App 怎么对接、字段怎么填 |
| 典型内容 | EARS 需求、公式、硬拒绝/幂等规则、锚点、golden 算例 | 十二段实现细节、SQL 治理、E2E 执行、调试 | 载荷字段全表、SQLite DDL、HTTP/Vue 示例、FAQ |
| 变更触发 | 行为/规则/契约变化 | 实现方式变化 | 对接方式变化 |

**冲突裁决**：规则冲突时以"规格＋代码＋E2E"为准；指南落后于规格时，在本集 README 兼容矩阵中标
"待吸收"，并按第 7 节流程回改指南。规格正文只放列名级表格与代码锚点，不放完整 JSON 载荷与 DDL。

## 3. 文件索引与需求 ID 区间

| 文件 | 内容 | 定义的需求 ID 区间 |
|---|---|---|
| [00-overview.md](00-overview.md) | 背景、范围、上下文、约束、术语、状态机 | — |
| [01-requirements.md](01-requirements.md) | 业务边界裁决、业务级需求、本期不做项 | REQ-BIZ-001~005、101~104、901~905 |
| [02-assessment-engine-spec.md](02-assessment-engine-spec.md) | 5230 四级评定引擎（3.2/3.3/B.1.3/3.4） | REQ-EVAL-101~801 |
| [03-sync-pipeline-spec.md](03-sync-pipeline-spec.md) | sync/upload 十二段管线 | REQ-SYNC-101~504 |
| [04-attachment-spec.md](04-attachment-spec.md) | 通道 A 附件预上传与同步 | REQ-ATT-101~404 |
| [05-offline-package-spec.md](05-offline-package-spec.md) | user/common/单桥三类离线包 | REQ-PKG-101~303 |
| [06-data-api-contract.md](06-data-api-contract.md) | API 端点、认证域、错误信封 | REQ-API-101~307 |
| [07-verification-traceability.md](07-verification-traceability.md) | golden 算例、T1/T2/T3 追溯矩阵、验证命令 | — |

ID 一经分配**永不复用、永不重排**；废止条目保留编号与 SUPERSEDED 状态并指向替代条目。

## 4. 推荐阅读路径

- **评定引擎开发/评审**：00 → 02 全篇 → 07 的 golden 表与 T1（3.2/3.3/3.4/附录 B 分组）。
- **同步管线开发**：00 → 01（业务边界）→ 03 → 04/05 → 07 的 E2E 编号表。
- **App 端对接**：01 的云端/现场职责表 → 03 的载荷键与业务边界 → 06 的端点表 → APP 指南 §3/§4 字段全表。
- **验收/交付评审**：01 全篇 → 07 全篇（golden＋T1 合规索引＋T2 测试反查＋T3 缺口）。

## 5. 需求条目约定（摘要）

条目头四元组：**状态**（DRAFT/ACTIVE/SUPERSEDED）｜**优先级**（P0 验收阻断 / P1 本期必须 / P2 兼容留痕）
｜**来源**（`STD:条款号` 标准条款、`DEC:日期或版本` 业务裁决、`CODE` 现状契约；`GUIDE:` 仅交叉引用）
｜**EARS 类型**（普遍 / 事件 / 状态 / 可选 / 非预期）。完整模板与实例见
[00-overview.md §6](00-overview.md)。锚点格式"符号优先、行号辅助"：
`bi-biz/.../SpanEvaluator.java:52-64（SpanEvaluator#evaluate）`。

## 6. 版本兼容矩阵

| SDD 版本 | 后端指南 | APP 指南 | 基线 commit | stdVersion | 备注 |
|---|---|---|---|---|---|
| v1.0（2026-10-30） | v1.5 | v1.6 | `843c0626` | 5230-2026 | 初版：引擎/管线/附件/离线包整批收口 |

规格独立语义化版本：MAJOR＝评定模型或规范换版；MINOR＝需求新增/状态变更/不做项复活；
PATCH＝锚点订正、措辞、矩阵补漏（不改验收语义）。

## 7. 变更流程

1. **同 PR 原则**：触碰任何 REQ 条目所述行为的代码 PR，必须同 PR 更新条目陈述、代码锚点、
   篇末紧凑矩阵与变更记录；纯重构只刷新行号。
2. **新行为无规格不入主干**：理想次序为 DRAFT → 评审 ACTIVE → 编码（SDD 正向）；现状补档允许
   代码先行但当次补齐。
3. **改公式先改 golden**：任何 EVAL-1xx/2xx/3xx/4xx/5xx 变更必须同步新增/修订 07 的 golden 行。
4. **季度对账**：执行 [07 §6](07-verification-traceability.md) 的五项文档自校验与锚点抽查，清扫 T3 缺口。
5. 合入后更新各篇篇头"基线 commit"与本文件兼容矩阵；不推送由提交者按仓库惯例自行决定。
