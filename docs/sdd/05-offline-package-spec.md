# 05 三类离线包规格

- 文档版本：v1.0 ｜ 最后更新：2026-10-30 ｜ 基线 commit：`843c0626`
- 代码锚点：`bi-biz/src/main/java/edu/whut/cs/bi/biz/service/impl/SqliteService.java`
  （下文仅给行号）；触发端点在
  `bi-api/src/main/java/edu/whut/cs/bi/api/controller/ApiControllerV2.java`。
- SQLite 表 DDL 全表与 App 端读法不在本篇，见
  [APP 离线同步开发指南](../../doc/APP%20离线同步开发指南.md) §4。

## 1. 三类包总览

| 包 | 触发 | 表数 | 内容范围 | MinIO 逻辑名（old_name） |
|---|---|---|---|---|
| 用户包 user.db | 手动 POST 或 sync 成功闭环（REQ-PKG-301） | 14 | 用户可见项目→任务→桥梁的全部业务数据 + 5230 人工三表 | `user_{userId}.db`（:123） |
| 通用字典包 common | POST `/api/v2/common/sqlite` | 11 | 仅 5230-2026 字典与绑定 | `common_base.db`（:156） |
| 单桥包 | POST `/api/v2/building/{id}/package` | 7（.db 数据块，随 ZIP 附件包发布） | 单桥检查链数据 | building 临时文件 |

## 2. 包内容（REQ-PKG-101~107）

#### REQ-PKG-101 用户包 14 表与范围
- ACTIVE ｜ P0 ｜ CODE ｜ 普遍式
- 需求陈述：用户包应恰好包含 14 张表：bi_project、bi_building、bi_task、bi_object、bi_element、
  bi_disease、bi_disease_detail、bi_attachment、bi_file_map、bi_span_element_part、bi_property、
  bi_assess_manual_input、bi_assess_absent_mark、bi_assess_single_control_mark；数据范围为该用户
  关联项目（projectMapper 按 userId）下全部任务与桥梁；人工判定三表按任务 id 集合
  selectByTaskIds 导出（:240-245）；bi_property 按各桥 rootPropertyId 全子树导出（:252-268）。
- 验收准则：znjc_demo 用户包 14 表齐全，人工三表行与其任务集合一致；无项目用户不出包
  （REQ-PKG-204）。
- 代码锚点：doGenerateUserSqlite :209-273；建表清单 :231-233
- 测试锚点：SqliteServiceTest 9 例；E2E-APP-001 Stage 1（user_stage1.db 校验）

#### REQ-PKG-102 通用字典包仅含 5230 字典
- ACTIVE ｜ P0 ｜ DEC:2026-10-13 ｜ 普遍式
- 需求陈述：通用包应恰好包含 11 张表：bi_template_object、bi_template_object_leaf_object、
  bi_leaf_object、bi_lo_disease_type、bi_disease_type、bi_disease_scale、bi_lo_disease_position、
  bi_material_durability、bi_repair_strengthening_structure、bi_md_disease_type、
  bi_rss_disease_type；其中带 std_version 列的表只导 std_version='5230-2026' 行，旧 H21
  模板/病害类型/标度不下发；4 张无 std_version 列的绑定表（lo/md/rss/绑定关系）种子只含 5230
  数据，按全量导出；bi_disease_type 行必须带 scale_type 列（EDI 等类型判别由此驱动）。
- 验收准则：common 包中 bi_template_object 仅 B01–B07 相关 5230 行；bi_disease_type 每行可读
  scale_type。
- 代码锚点：doGenerateCommonBaseSqlite :290-343（过滤注释 :291-292；建表 :325-328）
- 测试锚点：SqliteServiceTest（5230 过滤断言）

#### REQ-PKG-103 单桥包 7 表
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式：单桥 .db 数据块应恰好包含 bi_object、bi_element、
bi_attachment、bi_disease、bi_disease_detail、bi_file_map、bi_span_element_part 7 张表，
数据经 exportInspectionData 限定单桥；ZIP 附件包由 BuildingService 装配与登记。
- 代码锚点：doGenerateBuildingSqlite :275-288（清单 :282-283）；ZIP 端点
  ApiControllerV2:96-104
- 测试锚点：SqliteServiceTest（单桥导出）

#### REQ-PKG-104 四张评定结果表不出任何包
- ACTIVE ｜ P0 ｜ DEC ｜ 普遍式：bi_assess_element_result、bi_span_evaluation、
bi_assess_unit_result、bi_assess_bridge_result 不得出现在三类包的任一张建表清单中；评定结果
仅服务端在线查询（REQ-API-104）。
- 验收准则：对三个建表清单做文本检查均无结果表名；App 离线库不含结果表。
- 代码锚点：清单 :231-233、:282-283、:325-328（无结果表）
- 测试锚点：SqliteServiceTest（表清单断言）、E2E-APP-001

#### REQ-PKG-105 下发行的行语义
- ACTIVE ｜ P0 ｜ DEC:2026-10-27 ｜ 普遍式
- 需求陈述：包内业务行应携带服务端主键（App 侧读为 server_id）且云端下发行
  is_offline_data=0；App 对下发行只读引用（ROOT/UNIT/任务/桥梁），现场新增行
  is_offline_data=1 并以 offline_uuid 为本地主键；现场节点 parentUuid 可指向下发行
  server 节点（回查兜底见 REQ-SYNC-202）。
- 验收准则：样例库 user_stage1_span_part_added.db 中 ROOT/UNIT 行 server_id 9201–9203、
  flag=0；E2E-APP-001 Stage 1 同断言。
- 代码锚点：insert* 导出方法（行直取服务端列值，如 insertBiObjects/insertBuildings）
- 测试锚点：E2E-APP-001 Stage 1；`app-end/README.md` 样例库说明

#### REQ-PKG-106 双树合并导出与去重
- ACTIVE ｜ P1 ｜ DEC:v1.6 ｜ 普遍式
- 需求陈述：检查数据导出应同时收集桥梁的旧规范树根（root_object_id）与新规范树根
  （new_root_object_id）以支撑病害趋势多期比对；多根收集的对象集合应按 id distinct 去重，
  病害按 buildingIds 一次性查询，不在双树间产生重复行。
- 代码锚点：exportInspectionData :345-364（双根 :349-354、去重 :354/:364；
  病害 :367；详情 :369-370）
- 测试锚点：SqliteServiceTest（双树桥梁包不重复）

#### REQ-PKG-107 附件三类路由、缩略图双发与锚定随包
- ACTIVE ｜ P0 ｜ DEC:v1.2 ｜ 普遍式
- 需求陈述：包内附件应按主体三类收齐：病害照片（subject∈病害 id）、桥梁正/侧立面
  （type=6，subject∈buildingIds）、PART 当前照片（type=8，subject∈对象 id 集合）；bi_file_map
  应同时包含原图与缩略图 id（minioId/thumbMinioId 双发去重）；bi_span_element_part 锚定行
  随包供 App 离线读取。
- 验收准则：含三类照片的桥梁出包后，attachments 行数与云端一致，file_map 含原图与缩略图，
  spanElementParts 非空。
- 代码锚点：病害附件 :372-374；type=6 :380-384；type=8 :385-389；双图 id 收集 :391-397；
  落库 :399-407
- 测试锚点：E2E-ATT-001~004（出包回读）、E2E-APP-001

## 3. 行语义、存储与对象命名（REQ-PKG-201~204）

#### REQ-PKG-201 表结构列例外
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式：随包表结构以服务端 schema 复制为准，两处列例外须在 App DDL
中保持：bi_building 无 is_offline_data 列（桥梁本体不下发现场新增）；bi_disease_detail 无
审计列（create_by/create_time/update_by/update_time）。
- 验收准则：app-end 样例库生成器的 14 表 DDL 与服务端导出列集一致（该生成器 DDL 逐字摘自
本服务表定义）。
- 代码锚点：createTables/建表 DDL 段 :484 起；旁证 `app-end/make_demo_user_db.py`
- 测试锚点：E2E-APP-001（样例库可被管线消费）

#### REQ-PKG-202 SQLite WAL 与事务
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式：每个出包库连接应执行 PRAGMA journal_mode=WAL、
PRAGMA synchronous=NORMAL，并在手动事务（autoCommit=false）内成批插入后统一 commit。
- 代码锚点：connect :412-428（:423-426）；commit :270/:285/:340
- 测试锚点：SqliteServiceTest（生成库可读、数据完整）

#### REQ-PKG-203 MinIO 随机对象名与逻辑名分离
- ACTIVE ｜ P0 ｜ CODE ｜ 普遍式
- 需求陈述：包对象的存储对象名应为服务端随机生成的 32 位十六进制 + `.db`（
  UUID 去横线），在桶内按前 2 字符分桶（`xx/xxxx….db`），contentType 固定
  application/x-sqlite3；`user_{userId}.db`、`common_base.db` 等**逻辑名只写入
  bi_file_map.old_name**，new_name 存随机对象名；用户包另以 bi_user_sqlite（upsert，
  del_flag='0'）记录当前 minioId/包时间/大小，下载 URL 由 new_name 前 2 字符拼桶生成。
- 验收准则：连续两次为同一用户出包产生两个不同随机对象名，bi_user_sqlite 仅一行指向最新；
  old_name 恒为 user_{id}.db。
- 代码锚点：uploadToMinio :444-461（对象名 :445、分桶 :449、contentType :451、
  old/new :454-459）；updateUserSqliteRef :463-480；URL 拼装 :172-174
- 测试锚点：SqliteServiceTest（对象名/引用更新）

#### REQ-PKG-204 无数据不出包
- ACTIVE ｜ P1 ｜ CODE ｜ 非预期式：如果用户无关联项目（:212-213）、buildingId 对应桥梁不存在
（:277-278），生成方法应返回 null/空，由触发方投影为业务错误而非产生空库。
- 代码锚点：:211-213、:276-278；控制器投影 ApiControllerV2:86-89、:132-134
- 测试锚点：ApiControllerV2Test（空用户错误信封）

## 4. 闭环重打包与触发（REQ-PKG-301~303）

#### REQ-PKG-301 sync 成功后闭环重打包
- ACTIVE ｜ P0 ｜ CODE ｜ 事件式：当 sync/upload 全段成功且识别为 App 用户时，控制器应同步调用
generateUserSqliteSync 重建用户包、更新 bi_user_sqlite，并在响应 data 返回新包 SqliteVo；
生成失败返回 null 时投影错误且不影响已提交同步（与 REQ-SYNC-503 同事件的两侧）。
- 代码锚点：ApiControllerV2:145-174（:157-166）；generateUserSqliteSync :114-134
- 测试锚点：E2E-APP-001（同步响应含新 SqliteVo，包可回读新数据）

#### REQ-PKG-302 临时文件清理
- ACTIVE ｜ P2 ｜ CODE ｜ 普遍式：每次出包的本地临时 .db 文件应在 finally 中删除
（cleanupTempFile），异常路径也不残留。
- 代码锚点：:130-132、:162-164；cleanupTempFile :439-442
- 测试锚点：SqliteServiceTest

#### REQ-PKG-303 手动触发与查询端点
- ACTIVE ｜ P1 ｜ CODE ｜ 普遍式：系统应提供 GET/POST `/api/v2/user/{id}/sqlite`（查询当前包/
手动重生成）、POST `/api/v2/common/sqlite`、GET/POST `/api/v2/building/{id}/package`；查询无包
时返回业务错误信封（HTTP 投影与权限见 [06](06-data-api-contract.md)）。
- 代码锚点：ApiControllerV2:62-76、:81-90、:96-121、:126-139
- 测试锚点：ApiControllerV2Test 21 例

## 5. 明确不收录项

- `SqliteService#isDebounced`（:430-437）及 lastTriggerTime/DEBOUNCE_MS 在当前基线**无任何
  调用方**，属未启用代码，不构成规格承诺；未来启用时须先补条目（DRAFT→ACTIVE）与测试。

## 篇末追溯矩阵

| 需求 ID | 代码符号锚点 | 主要测试锚点 | 验收状态 |
|---|---|---|---|
| REQ-PKG-101 | doGenerateUserSqlite :209-273 | SqliteServiceTest、E2E-APP-001 | 单测+E2E |
| REQ-PKG-102 | doGenerateCommonBaseSqlite :290-343 | SqliteServiceTest | 单测 |
| REQ-PKG-103 | doGenerateBuildingSqlite :275-288 | SqliteServiceTest | 单测 |
| REQ-PKG-104 | 三个建表清单 | SqliteServiceTest、E2E-APP-001 | 单测+E2E |
| REQ-PKG-105 | insert* 导出方法 | E2E-APP-001 Stage 1、app-end 样例库 | E2E |
| REQ-PKG-106 | exportInspectionData :345-364 | SqliteServiceTest | 单测 |
| REQ-PKG-107 | exportInspectionData :372-407 | E2E-ATT-001~004、E2E-APP-001 | E2E |
| REQ-PKG-201 | 建表 DDL 段 :484+；app-end 生成器 | E2E-APP-001 | E2E |
| REQ-PKG-202 | connect :423-426 | SqliteServiceTest | 单测 |
| REQ-PKG-203 | uploadToMinio :444-461；:463-480 | SqliteServiceTest | 单测 |
| REQ-PKG-204 | :212-213、:277-278 | ApiControllerV2Test | 单测 |
| REQ-PKG-301 | V2:145-174；generateUserSqliteSync :114 | E2E-APP-001 | 单测+E2E |
| REQ-PKG-302 | cleanupTempFile :439-442 | SqliteServiceTest | 单测 |
| REQ-PKG-303 | ApiControllerV2:62-139 | ApiControllerV2Test | 单测 |
