# 04 附件通道 A 规格（预上传与挂接）

- 文档版本：v1.0 ｜ 最后更新：2026-10-30 ｜ 基线 commit：`843c0626`
- 服务锚点：`bi-biz/src/main/java/edu/whut/cs/bi/biz/service/impl/SyncAttachmentServiceImpl.java`
  （下文仅给行号）；HTTP 端点锚点：
  `bi-api/src/main/java/edu/whut/cs/bi/api/controller/ApiControllerV2.java`
- 元数据挂接（sync 第 8 段）规则在 [03 REQ-SYNC-305/306](03-sync-pipeline-spec.md)，本篇只定义
  文件预上传与回查。

## 1. 通道模型

| 通道 | 路径 | 状态 |
|---|---|---|
| **A：幂等预上传 + sync 元数据挂接** | `/api/v2/sync/attachment`、`/batch`、`/query` + sync 第 8 段 | 在役，本篇主体 |
| 旧版兼容：裸 minioId | `/api/v2/sync/attachment` 不带 uploadUuid | 保留（REQ-ATT-107） |
| **B：OSS ZIP 批量** | `/api/v2/sync/attachment/oss` | 端点保留、**本期暂停验收**（REQ-BIZ-905④、REQ-ATT-402） |

通道 A 时序：App 先逐文件预上传拿 minioId/thumbMinioId → 拍照元数据进离线库 → 随 sync
attachments 数组第 8 段落 bi_attachment；预上传阶段**不写 bi_attachment**。

## 2. 单文件预上传（REQ-ATT-101~107）

入口 `preUpload(rawUploadUuid, file)`（:64），返回 UploadItemResultVo。

#### REQ-ATT-101 uploadUuid 归一化与格式校验
- ACTIVE ｜ P0 ｜ CODE ｜ 普遍式
- 需求陈述：系统应将 uploadUuid 做 trim、转小写、去横线后按 `^[0-9a-f]{32}$` 校验；非法（含
  null）时返回 failed，错误文案"uploadUuid 非法（需为32位UUID）"，不产生任何文件流量。
- 验收准则：带横线大写 UUID 与去横线小写 UUID 视为同一键；"xyz" 返回 failed 且 MinIO 无新增对象。
- 代码锚点：`normalizeUploadUuid` :213-220；非法返回 :66-68
- 测试锚点：SyncAttachmentServiceTest（非法/归一化用例）、E2E-ATT-007

#### REQ-ATT-102 登记快路径与残缺登记硬失败
- ACTIVE ｜ P0 ｜ DEC:v1.2 ｜ 事件式
- 需求陈述：当 bi_file_upload_registry 已存在该 uploadUuid 时，系统应零流量直接返回既有
  fileMapId/thumbFileMapId（exists=true）；如果登记表指向的 bi_file_map 行不存在（人工清理等
  残缺状态），应返回 failed"服务端登记状态不一致，请联系管理员"，**不得**自动重传而使 App
  无感知地更换 minioId。
- 验收准则：同 uuid 第二次上传不同文件内容，返回的 minioId 与首次一致且 MinIO 对象数不增；
  手工删掉 bi_file_map 行后返回 failed。
- 代码锚点：快路径 :77-86；残缺错误 :79-84
- 测试锚点：E2E-ATT-006（重传零流量）、SyncAttachmentServiceTest 残缺登记用例

#### REQ-ATT-103 空文件与 20MB 上限
- ACTIVE ｜ P0 ｜ CODE ｜ 非预期式
- 需求陈述：如果文件为空或大小超过 `bi.sync.attachment.max-file-bytes`（默认 20MB=20971520），
  系统应在触碰存储前返回 failed（"文件为空"/"超出单文件大小限制"）。
- 代码锚点：配置 :57-58；校验 :69-74
- 测试锚点：SyncAttachmentServiceTest（空/超限用例）

#### REQ-ATT-104 服务端缩略图
- ACTIVE ｜ P1 ｜ DEC:v1.2 ｜ 可选式
- 需求陈述：在 `bi.sync.attachment.thumbnail-enabled=true`（默认）且原图为图片时，系统应按
  1024×768 居中裁剪、质量 0.5 生成缩略图并独立入 MinIO/bi_file_map，thumbFileMapId 记入登记；
  缩略图生成或存储失败只告警、thumbFileMapId=null，不阻断原图成功；非图片文件不生成缩略图。
- 验收准则：jpg 上传成功且 thumbMinioId 非空；模拟缩略图异常时原图 minioId 仍返回；txt 文件
  thumbMinioId 为 null。
- 代码锚点：开关 :60-61；生成 :103-111；`ThumbPhotoUtils.createThumbnail(tmp,1024,768,0.5f)`
  :105；图片判定 `fileMapService.isImageFile` :103
- 测试锚点：SyncAttachmentServiceTest（缩略图/非图片/失败不阻断）、E2E-ATT-008

#### REQ-ATT-105 处理序与并发负方补偿
- ACTIVE ｜ P0 ｜ CODE ｜ 事件式
- 需求陈述：系统应按固定顺序处理：临时文件 → 原图 storeFromFile（MinIO + bi_file_map；
  putObject 成功而 DB 插入失败时由 storeFromFile 自删对象）→ 缩略图 → 插登记表；当并发同键
  双发导致登记表唯一键冲突（DuplicateKeyException）时，负方应补偿删除自己刚写入的原图/缩略图
  对象与 bi_file_map 行，返回胜方结果（exists=true）；胜方记录不可读时返回 failed
  "并发上传冲突，请重试"。
- 验收准则：两线程同 uploadUuid 并发成功返回后，MinIO/bi_file_map/登记各仅一份且两侧 minioId
  一致。
- 代码锚点：顺序 :92-123；冲突分支 :124-137；补偿 `compensateDeleteQuietly` :243-252
- 测试锚点：SyncAttachmentServiceTest（并发双发/负方补偿）、E2E-ATT-009

#### REQ-ATT-106 单文件结果三态与登记字段
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式
- 需求陈述：预上传结果应为 success（首次）/ exists（登记快路径或并发胜方返回）/ failed 三态，
  成功载荷含 uploadUuid、minioId、thumbMinioId；登记表应留痕 originalName（截 255）、
  fileSize、contentType、userId、createBy/createTime。
- 代码锚点：首次成功 :139；exists :85、:132-133；failed 各点；登记装配 :114-123；
  截断 `truncate` :235-240
- 测试锚点：SyncAttachmentServiceTest 14 例；E2E-ATT-006~009

#### REQ-ATT-107 旧版裸 minioId 兼容路径
- ACTIVE ｜ P2 ｜ DEC（历史兼容）｜ 状态式
- 需求陈述：当请求不带 uploadUuid 时，系统应走旧版 handleFileUpload 路径：响应 data 为裸
  Integer（bi_file_map.id），不登记 bi_file_upload_registry、不生成缩略图、不可回查；该路径
  仅为旧版 App 保留，新功能不得依赖。
- 验收准则：无 uploadUuid 的上传 data 为数字；bi_file_upload_registry 无行。
- 代码锚点（控制器）：ApiControllerV2:190-197
- 测试锚点：E2E-ATT-010（旧路径兼容）

## 3. 批量预上传（REQ-ATT-201~203）

#### REQ-ATT-201 等长对齐与整请求校验
- ACTIVE ｜ P0 ｜ CODE ｜ 非预期式
- 需求陈述：批量请求中 files 与 uploadUuids 必须等长、按下标对齐；files 为空、两数组不等长或
  文件数超过 `bi.sync.attachment.max-batch-size`（默认 20）时，系统应整请求返回错误且不处理
  任何文件。
- 代码锚点：ApiControllerV2:221-229；服务空值/不等长返回空结果 :152-154；配置
  ApiControllerV2:53-54
- 测试锚点：E2E-ATT-011（超限/不等长）、ApiControllerV2Test

#### REQ-ATT-202 批内重复确定性失败
- ACTIVE ｜ P0 ｜ CODE ｜ 非预期式
- 需求陈述：如果同一批次内出现重复 uploadUuid（归一化后），系统应只处理第一项，其余项
 确定性 failed"批内 uploadUuid 重复"，而不是静默覆盖或都命中快路径。
- 代码锚点：:155-167（seen 去重 :164-166）
- 测试锚点：SyncAttachmentServiceTest（批内重复）

#### REQ-ATT-203 逐项成败收敛与批内顺序执行
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式
- 需求陈述：系统应在服务端按序逐项执行（缩略图解码为 CPU 密集型，不并发放大），每项成败收敛
  到 data.items[]；单项 failed 不影响同批其他项；App 侧并发由批间控制（建议 ≤3）。
- 代码锚点：:168-170（顺序调 preUpload）；控制器 data 装配 :231-232
- 测试锚点：E2E-ATT-011（混合成败批）

## 4. 回查（REQ-ATT-301）

#### REQ-ATT-301 按 uploadUuid 批量回查
- ACTIVE ｜ P0 ｜ DEC:v1.2 ｜ 普遍式
- 需求陈述：系统应支持一次 ≤500 个 uploadUuid 的回查：服务端单次 IN 查询登记表，输出按请求
  顺序排列（含重复键的重复输出）；未登记或登记指向的 bi_file_map 已缺失的键返回
  uploaded=false；已登记且原图存在的返回 uploaded=true 及 minioId/thumbMinioId。空列表/超 500
  为请求级错误（见 REQ-API-203）。
- 验收准则：混合 [已传, 未传, 已传] 三键回查，顺序一致、布尔位正确，且全程无文件流量。
- 代码锚点：`queryUploaded` :175-211（归一化去重 :183-189、IN 查询 :191、保序输出 :196-209、
  残缺判 false :201）；数量限制 ApiControllerV2:250-252
- 测试锚点：E2E-ATT-007（同步前跳过已传/响应丢失恢复）、SyncAttachmentServiceTest

## 5. 元数据与边界（REQ-ATT-401~404）

#### REQ-ATT-401 预上传不落业务挂接
- ACTIVE ｜ P0 ｜ DEC:v1.2 ｜ 普遍式：预上传只产 MinIO 对象、bi_file_map 行与登记表行；
bi_attachment 业务行只在 sync 第 8 段按 type 路由写入（REQ-SYNC-305），缺 minioId 的同步行
按 REQ-SYNC-306 判错。
- 代码锚点：preUpload 全程无 attachmentMapper；挂接 SyncUploadServiceImpl:457-486
- 测试锚点：E2E-ATT-001~004（挂接后才有 bi_attachment 行）

#### REQ-ATT-402 通道 B 暂停
- ACTIVE ｜ P2 ｜ DEC（REQ-BIZ-905④）｜ 状态式：`/api/v2/sync/attachment/oss` 端点与 OSS 拉取
解压实现保留在代码库，但本期不承诺行为、不纳入验收；复活需 MINOR 规格版本 + E2E。
- 代码锚点：ApiControllerV2:270-290+（实现保留）
- 测试锚点：无（暂停项）

#### REQ-ATT-403 文件名净化与临时文件清理
- ACTIVE ｜ P1 ｜ CODE ｜ 非预期式
- 需求陈述：系统应剥离客户端文件名中的路径部分（只取 name 段，空名兜底 photo.jpg），防止路径
  注入；每次预上传的原图/缩略图临时文件必须在 finally 中删除，删除失败时 deleteOnExit。
- 代码锚点：`sanitizeOriginalName` :226-233；临时名 :94；清理 :143-146、`deleteQuietly`
  :254-258
- 测试锚点：SyncAttachmentServiceTest（路径型文件名用例）

#### REQ-ATT-404 登记表 DDL 是启用前置
- ACTIVE ｜ P0 ｜ DEC:v1.2 ｜ 状态式：通道 A 依赖 bi_file_upload_registry 及
bi_file_map.offline_uuid 唯一键等 DDL；未执行对应"部分"DDL 的环境不得开启通道 A 流量
（生产 DDL 由人工执行，见 [00 §4](00-overview.md) DDL 治理）。
- 代码锚点：唯一键冲突处理 :124-137（以 uk 存在为前提）；DDL 位置见后端数据同步开发指南
- 测试锚点：测试环境 all_in_one DDL 预检（07 §1）

## 篇末追溯矩阵

| 需求 ID | 代码符号锚点 | 主要测试锚点 | 验收状态 |
|---|---|---|---|
| REQ-ATT-101 | normalizeUploadUuid :213-220 | SyncAttachmentServiceTest、E2E-ATT-007 | 单测+E2E |
| REQ-ATT-102 | 快路径 :77-86 | E2E-ATT-006、残缺登记单测 | 单测+E2E |
| REQ-ATT-103 | :57-74 | SyncAttachmentServiceTest | 单测 |
| REQ-ATT-104 | :103-111 | 缩略图/非图片/失败不阻断用例、E2E-ATT-008 | 单测+E2E |
| REQ-ATT-105 | :92-137、compensateDeleteQuietly | 并发双发用例、E2E-ATT-009 | 单测+E2E |
| REQ-ATT-106 | :114-139 | SyncAttachmentServiceTest、E2E-ATT-006 | 单测+E2E |
| REQ-ATT-107 | ApiControllerV2:190-197 | E2E-ATT-010 | E2E |
| REQ-ATT-201 | ApiControllerV2:221-229 | E2E-ATT-011、ApiControllerV2Test | 单测+E2E |
| REQ-ATT-202 | :155-167 | 批内重复单测 | 单测 |
| REQ-ATT-203 | :168-170 | E2E-ATT-011 | 单测+E2E |
| REQ-ATT-301 | queryUploaded :175-211；V2:250 | E2E-ATT-007 | 单测+E2E |
| REQ-ATT-401 | preUpload 无挂接；SyncUploadServiceImpl:457-486 | E2E-ATT-001~004 | E2E |
| REQ-ATT-402 | ApiControllerV2:270+ | 无（暂停项） | 仅手工 |
| REQ-ATT-403 | sanitizeOriginalName :226-233、:143-146 | 路径文件名用例 | 单测 |
| REQ-ATT-404 | uk 冲突处理 :124（DDL 前置） | 测试环境 DDL 预检 | 仅手工 |
