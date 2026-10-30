# 06 API 端点与数据契约规格

- 文档版本：v1.0 ｜ 最后更新：2026-10-30 ｜ 基线 commit：`843c0626`
- 本篇只规定端点、方法、权限、响应形态与错误投影。载荷字段全表见
  [APP 离线同步开发指南](../../doc/APP%20离线同步开发指南.md) §3/§4，业务规则见
  [02](02-assessment-engine-spec.md)/[03](03-sync-pipeline-spec.md)/[04](04-attachment-spec.md)/[05](05-offline-package-spec.md)。
- 统一信封：RuoYi AjaxResult（`code`/`msg`/`data`）；业务失败不使用 HTTP 5xx，而是 HTTP 200 +
  信封错误码（REQ-API-303）。

## 1. 5230 评定端点（REQ-API-101~106）

基址 `/biz/assess5230`，控制器
`bi-biz/src/main/java/edu/whut/cs/bi/biz/controller/Assess5230Controller.java`（下文仅给行号）。
全部端点**无在线录入**（REQ-BIZ-104），calculate 仅后端内部触发（REQ-BIZ-103）。

#### REQ-API-101 五端点清单
- ACTIVE ｜ P0 ｜ CODE ｜ 普遍式：系统应恰好暴露以下 5 个评定端点，不允许多出录入/删除端点：

| 方法 | 路径 | 权限 | data 内容 | 行号 |
|---|---|---|---|---|
| GET | `/biz/assess5230/task/{taskId}/tree` | biz:assess5230:view | 结构解析树 + warnings（只读预览） | :45-53 |
| GET | `/biz/assess5230/task/{taskId}/inputs` | biz:assess5230:view | 当前人工三表输入回查 | :57-65 |
| POST | `/biz/assess5230/task/{taskId}/calculate` | **biz:assess5230:calculate** | 计数响应（REQ-API-306），@Log 审计 | :68-79 |
| GET | `/biz/assess5230/task/{taskId}/result` | biz:assess5230:view | {bridge, units, spans, elements} 四层结果 | :82-91 |
| GET | `/biz/assess5230/singleIndicators` | biz:assess5230:view | 16 项指标 [{no,name}] | :94-106 |

- 验收准则：控制器仅有上述 5 个映射；类 Javadoc 声明单通道与 calculate 内部触发（:25-32）。
- 测试锚点：Assess5230CalculationE2ETest（E2E-5230-001~004）、E2E-HTTP 系列

#### REQ-API-102 tree 预览无副作用
- ACTIVE ｜ P0 ｜ CODE ｜ 普遍式：tree 端点只做结构解析并返回 units 树与非阻断 warnings，不得写
任何表；业务异常以错误信封返回（同 REQ-EVAL-716）。
- 代码锚点：:43-53；服务 previewTree（Assess5230CalculateService.java:140-149）
- 测试锚点：E2E-5230-001（预览后结果表无行）

#### REQ-API-103 calculate 触发契约
- ACTIVE ｜ P0 ｜ CODE ｜ 事件式：calculate 为 POST、需 biz:assess5230:calculate 权限点、记
@Log（title "5230桥梁评定计算"，BusinessType.OTHER）；操作人取 ShiroUtils.getLoginName() 传入
服务层；重复 POST 幂等（REQ-EVAL-713）。
- 验收准则：无权限用户 403/鉴权失败；两次 calculate 后结果表行数不翻倍。
- 代码锚点：:67-79
- 测试锚点：E2E-5230-001（幂等）、权限用例（手工，见 07 T3）

#### REQ-API-104 result/inputs/singleIndicators 只读契约
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式：result 返回最近一次评定的四层结果；inputs 回查三表当前行；
singleIndicators 固定返回 16 行 no/name（枚举见 SingleIndicator:10-25），无结果时各层为空结构
而非报错（未计算时 result 的具体空形态以服务 getResult 实现为准）。
- 代码锚点：:55-65、:81-106
- 测试锚点：E2E-5230-001/003（result 断言）

#### REQ-API-105 引擎错误统一信封投影
- ACTIVE ｜ P0 ｜ CODE ｜ 非预期式：如果服务层抛 ServiceException（非 5230 任务、结构残缺、锚定
非法、B08 闸门、分母为 0 等），控制器应捕获并以 AjaxResult.error(e.getMessage()) 返回，异常
不外泄为容器 500 页。
- 验收准则：B08 任务调 tree/calculate/result 均返回错误信封且消息含桥型/拒绝原因（E2E-5230-004
对 tree 与 calculate 两侧）。
- 代码锚点：五个端点各自的 catch（:50-52、:62-64、:76-78、:88-90）；服务层拒绝
  require5230Task（Assess5230CalculateService.java:639-651）
- 测试锚点：E2E-5230-004

#### REQ-API-106 引擎响应无 DTO 冻结
- ACTIVE ｜ P2 ｜ DEC ｜ 普遍式：评定端点响应保持 Map/List 装配形态（含 singleIndicators 的
List<Map>），本期不引入响应 DTO；字段增删以服务层装配 + E2E 断言为契约，变更必须同 PR 更新
本规格与对应 E2E。
- 代码锚点：:98-105；服务响应装配（Assess5230CalculateService.java:373-379）
- 测试锚点：全部 E2E-5230 字段断言

## 2. App V2 端点（REQ-API-201~205）

基址 `/api/v2`，控制器
`bi-api/src/main/java/edu/whut/cs/bi/api/controller/ApiControllerV2.java`（下文仅给行号）。

#### REQ-API-201 sync/upload 端点与响应分流
- ACTIVE ｜ P0 ｜ CODE ｜ 事件式
- 需求陈述：系统应提供 POST `/api/v2/sync/upload`，请求体为裸 JSON Map（无 DTO，REQ-SYNC-504）；
  全段成功后按当前会话用户分流响应：可识别用户 → 重打包成功 data=SqliteVo；重打包返回 null →
  错误信封"离线包生成失败"；重打包抛异常 → 错误信封"同步上传成功，但重新打包数据库时发生异常"
  （同步数据已提交不回滚）；无法识别用户 → 成功信封但 data=SyncResultVo（"同步完成，但未识别到
  当前用户"）；管线致命异常 → 错误信封"离线数据上传并同步处理异常: …"。
- 验收准则：E2E-APP-001 同步响应 data 含新包 url/timestamp/size；未识别用户场景 data 为
  successCount/errors。
- 代码锚点：:145-174
- 测试锚点：E2E-APP-001、ApiControllerV2Test 21 例

#### REQ-API-202 附件单文件双模式
- ACTIVE ｜ P0 ｜ DEC:v1.2 ｜ 状态式：POST `/api/v2/sync/attachment` 按 uploadUuid 有无分流
（REQ-ATT-101/107）：带 uuid 返回 UploadItemResultVo，failed 投影为
"附件上传失败: {error}"；不带 uuid 返回裸 Integer minioId；未取得文件记录返回
"上传失败，未获取到文件记录"；未捕获异常返回"附件上传异常"。
- 代码锚点：:184-207
- 测试锚点：E2E-ATT-006~010、ApiControllerV2Test

#### REQ-API-203 附件批量/回查的请求级校验
- ACTIVE ｜ P0 ｜ CODE ｜ 非预期式
- 需求陈述：POST `/sync/attachment/batch` 在 files 空、uploadUuids 与 files 不等长、数量超
  max-batch-size（默认 20）时应整请求错误且不存储任何文件；POST `/sync/attachment/query` 在
  uploadUuids 空或超 500 时整请求错误；逐文件/逐键成败仍收敛在 data 内（REQ-ATT-203/301）。
- 代码锚点：batch :216-237（:221-229）；query :243-259（:247-252）；上限配置 :53-54
- 测试锚点：E2E-ATT-011、ApiControllerV2Test

#### REQ-API-204 离线包触发/查询端点族
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式：系统应提供 GET/POST
`/api/v2/user/{id}/sqlite`（查询当前包/立即重生成；未生成返回"未生成或不存在"）、POST
`/api/v2/common/sqlite`、GET/POST `/api/v2/building/{id}/package`；buildingId 为空返回
"参数错误"。
- 代码锚点：:62-76、:81-90、:96-121、:126-139
- 测试锚点：ApiControllerV2Test 21 例

#### REQ-API-205 OSS ZIP 端点暂停
- ACTIVE ｜ P2 ｜ DEC ｜ 状态式：POST `/api/v2/sync/attachment/oss` 保留映射与实现，但属通道 B
暂停范围（REQ-BIZ-905④、REQ-ATT-402），本期不承诺响应契约、不纳入验收。
- 代码锚点：:270 起
- 测试锚点：无

## 3. 鉴权、错误信封与横切契约（REQ-API-301~307）

#### REQ-API-301 双认证域
- ACTIVE ｜ P0 ｜ CODE ｜ 状态式：`/biz/**`（含 /biz/assess5230/**）走 Web 端 Shiro 会话；
`/api/v2/**` 走 App JWT Bearer 认证（`/jwt/login` 换取 token）；两域互不混用，评定端点不在
/api/v2 暴露，sync/upload 不在 /biz 暴露。
- 验收准则：无 token 访问 /api/v2/sync/upload 鉴权失败；无会话访问 /biz/assess5230/** 鉴权失败
（具体状态码以全局安全配置投影为准，均不得为 200 成功信封）。
- 代码锚点：控制器分属 bi-biz（:35）与 bi-api（类映射 :34）；JWT 过滤器/Shiro 配置见安全模块
- 测试锚点：ApiControllerV2Test（鉴权用例）；E2E 前置登录 znjc_demo/znjc_test_1

#### REQ-API-302 权限点收敛为两个
- ACTIVE ｜ P0 ｜ CODE ｜ 普遍式：5230 功能仅使用 biz:assess5230:view 与
biz:assess5230:calculate 两个权限点；四个读端点共用 view；不设 edit 点（直连录入已删除，
REQ-BIZ-104）。
- 代码锚点：:44/:56/:68/:82/:94（@RequiresPermissions 全集）
- 测试锚点：Assess5230Controller 注解检查；E2E 以有权限账号执行

#### REQ-API-303 ServiceException → HTTP 200 业务错误信封
- ACTIVE ｜ P0 ｜ CODE ｜ 非预期式：业务规则失败应通过 AjaxResult.error 以 HTTP 200 + 信封错误
返回，消息为服务层原始文案（供 App/ Web 直接展示）；仅鉴权失败与框架级错误走非 200/全局异常
投影。
- 验收准则：缺 minioId、主体反查失败、非 5230 任务等均为 200 信封且 msg 含具体原因；管线逐行
错误不进 HTTP 错误，而在 data(SyncResultVo).errors 内（REQ-SYNC-104）。
- 代码锚点：Assess5230Controller 五处 catch；ApiControllerV2 各 try/catch
- 测试锚点：全部错误分支 E2E（HTTP/SVC/ATT/5230-004）

#### REQ-API-304 请求级缺陷不产生副作用
- ACTIVE ｜ P1 ｜ CODE ｜ 非预期式：空 files、数组不等长、超批量上限、空/超量 query、空 buildingId
等请求级缺陷必须在任何存储/同步动作之前拒绝；系统不得出现半批落库。
- 验收准则：21 个文件的批量请求后 MinIO/bi_file_map 无新增；sync 空载荷不产生 sync_log 成功态
之外的业务行。
- 代码锚点：:221-229、:247-252、:100-102
- 测试锚点：E2E-ATT-011、ApiControllerV2Test

#### REQ-API-305 非 5230 任务的错误投影
- ACTIVE ｜ P0 ｜ CODE ｜ 状态式：对非 5230-2026 任务（含空 taskId、任务不存在、无 building、
缺 new_root_object_id）调用 tree/inputs/calculate/result，应返回同一拒绝语义的错误信封
（服务闸门见 REQ-EVAL-711）。
- 代码锚点：Assess5230Controller 四端点 catch；Assess5230CalculateService:624-651
- 测试锚点：E2E-5230-004

#### REQ-API-306 calculate 响应计数
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式：calculate 成功 data 应至少含 bridge（全桥结果）、warnings
（非阻断告警列表）、unitCount、spanCount、elementRowCount 五个键，供前端校验计算规模。
- 验收准则：E2E-APP-001 响应 unitCount=2、spanCount=4，elementRowCount 与构件/幻影行数一致。
- 代码锚点：Assess5230CalculateService:373-379
- 测试锚点：E2E-APP-001、E2E-5230-001

#### REQ-API-307 字段契约的唯一出处分工
- ACTIVE ｜ P1 ｜ DEC ｜ 普遍式：sync/upload 与附件请求的字段级契约以 APP 指南字段全表为准，本篇
不复制；规则/值域以 03/04 为准；任一处变更必须同 PR 更新另一处与相关 E2E（同 PR 原则见
[README §7](README.md)）。
- 代码锚点：—（文档契约条目）
- 测试锚点：E2E 全量（契约保护）

## 篇末追溯矩阵

| 需求 ID | 代码符号锚点 | 主要测试锚点 | 验收状态 |
|---|---|---|---|
| REQ-API-101 | Assess5230Controller 五映射 :45-106 | E2E-5230-001~004 | E2E |
| REQ-API-102 | tree :43-53；previewTree:140 | E2E-5230-001 | E2E |
| REQ-API-103 | calculate :67-79 | E2E-5230-001 幂等；权限仅手工 | E2E（权限手工） |
| REQ-API-104 | inputs/result/singleIndicators :55-106 | E2E-5230-001/003 | E2E |
| REQ-API-105 | 五处 catch；require5230Task | E2E-5230-004 | E2E |
| REQ-API-106 | :98-105；CalculateService:373-379 | E2E-5230 字段断言 | E2E |
| REQ-API-201 | ApiControllerV2:145-174 | E2E-APP-001、ApiControllerV2Test | 单测+E2E |
| REQ-API-202 | :184-207 | E2E-ATT-006~010 | E2E |
| REQ-API-203 | :216-259 | E2E-ATT-011 | 单测+E2E |
| REQ-API-204 | :62-139 | ApiControllerV2Test | 单测 |
| REQ-API-205 | :270+ | 无（暂停项） | 仅手工 |
| REQ-API-301 | 控制器分模块；安全配置 | ApiControllerV2Test 鉴权用例 | 单测+手工 |
| REQ-API-302 | 五处 @RequiresPermissions | 注解检查、E2E 账号 | E2E（授权手工） |
| REQ-API-303 | 两端点族 catch 全集 | 错误分支 E2E | E2E |
| REQ-API-304 | :221-252、:100-102 | E2E-ATT-011 | 单测+E2E |
| REQ-API-305 | require5230Task:624-651 | E2E-5230-004 | E2E |
| REQ-API-306 | CalculateService:373-379 | E2E-APP-001、5230-001 | E2E |
| REQ-API-307 | —（文档契约） | E2E 全量 | E2E |
