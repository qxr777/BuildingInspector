# `/api/v2/sync/upload` 接口测试用例（可直接执行 + 可直接判定版）

> 目标：让测试同学在 PostApi/Apipost 中，清楚“每个 test 在验证什么、为什么要跑、跑完如何判定通过”。

---

## 0. 测试前你需要知道的三件事

1. `offlineUuid`：离线端记录唯一标识，用于服务端判断“新增还是更新”。
2. `syncUuid`：一次同步请求的批次号，用于排查日志与映射。
3. `rootObjectUuid/rootObjectId`：桥梁对象树入口。
   - 如果要走“桥梁部件结构查询”，必须保证 Building 有根对象映射。
   - 即：`buildings[].rootObjectUuid` 对应 `objects[]` 里真实存在的根对象。

---

## 1. 前置条件

1. 已通过 `/jwt/login` 获取有效 token。
2. 测试库可写，可执行 SQL。
3. 附件场景先调用 `/api/v2/sync/attachment` 获取 `minioId`。
4. 每个 test 使用独立 `syncUuid`，避免相互污染。

统一请求头：

```http
POST /api/v2/sync/upload
Authorization: Bearer {{token}}
Content-Type: application/json
```

---

## 2. 判定口径（统一标准，先看这节）

### 2.1 三层断言模板（每条用例都要做）

1. **响应断言**：HTTP 状态码、`code`、`msg` 关键字。
2. **数据断言**：目标记录新增/更新/删除是否符合预期，是否重复。
3. **日志断言**：`bi_sync_log` 中 `syncUuid` 对应 `status/remark` 是否合理。

### 2.2 错误码与结果解释建议口径

- 正常/可容错成功：`HTTP 200` 且 `code=0`
- 参数或类型不可解析：通常 `HTTP 200` 且 `code=500`
- 鉴权失败：`HTTP 401/403`
- 后置 SQLite 失败：可能 `code=0` 但 `msg` 提示“同步成功、打包失败”

### 2.3 事务语义说明（统一口径）

当前实现按“**方法级事务 + 单条容错记录错误**”执行，不是所有场景严格“全量回滚”。

---

## 3. 推荐执行顺序

1. test1 正常插入（建立基线数据）
2. test6 父子依赖（顺序）
3. test6-2 父子依赖（乱序，多轮处理）
4. test7 跨实体引用链（且可用于结构查询）
5. test8 附件正常映射
6. test10 新标关联
7. test2 更新已有 UUID
8. test11 幂等重复提交
9. test3 删除已存在
10. test3-2 附件删除语义
11. test4 删除不存在
12. test9 附件孤儿引用
13. test9-2 非附件引用缺失
14. test5 类型测试（多组 JSON）
15. test12 鉴权失败
16. test13 空载荷/缺 key
17. test14 SQLite 后置失败分支（可选）
18. test15 并发同 UUID 冲突（可选）

---

## 4. 通用 SQL（每条用例后都执行）

```sql
SELECT id, sync_uuid, user_id, status, create_time, finish_time, remark
FROM bi_sync_log
WHERE sync_uuid = 'test-sync-case-001';

SELECT table_name, COUNT(*) AS cnt
FROM bi_id_mapping
WHERE sync_uuid = 'test-sync-case-001'
GROUP BY table_name
ORDER BY table_name;

SELECT table_name, offline_uuid, server_id, create_time
FROM bi_id_mapping
WHERE sync_uuid = 'test-sync-case-001'
ORDER BY table_name, id;
```

> 记得每次把 `test-sync-case-001` 换成当前用例的 `syncUuid`。

---

## 5. 用例说明与请求体

## test1（正常插入最小闭环）

> 说明：以下“通过标准”仅适用于 `test1`。其他 test 以各自小节里的目标与判定为准。

**通过标准：**
- HTTP 200 且 `code=0`
- 通用 SQL 能看到 `bi_sync_log` 成功
- `bi_id_mapping` 有多种 `table_name` 映射

```json
{
  "syncUuid": "test-sync-case-001",
  "clientInfo": "ApiPost/Test1",
  "taskId": 1,
  "buildings": [{"offlineUuid":"t1-bld-001","name":"测试桥梁T1","isLeaf":"1","status":"0","offlineDeleted":0,"rootObjectUuid":"t1-obj-root-001"}],
  "objects": [
    {"offlineUuid":"t1-obj-root-001","name":"上部结构","parentUuid":"","buildingUuid":"t1-bld-001","status":"0","offlineDeleted":0},
    {"offlineUuid":"t1-obj-span-001","name":"第1跨","parentUuid":"t1-obj-root-001","buildingUuid":"t1-bld-001","status":"0","spanIndex":1,"spanLength":30.5,"offlineDeleted":0}
  ],
  "components": [{"offlineUuid":"t1-comp-001","name":"主梁1","code":"T1-GL-01","status":"0","objectUuid":"t1-obj-span-001","edi":1,"efi":0,"eai":0,"offlineDeleted":0}],
  "diseases": [{"offlineUuid":"t1-dis-001","buildingUuid":"t1-bld-001","objectUuid":"t1-obj-span-001","componentUuid":"t1-comp-001","taskId":1,"type":"裂缝","position":"腹板","positionNumber":1,"description":"离线裂缝","level":1,"quantity":1,"units":"m","deductPoints":1,"offlineDeleted":0}],
  "diseaseDetails": [{"offlineUuid":"t1-dd-001","diseaseUuid":"t1-dis-001","reference1Location":"距左端","reference1LocationStart":1.2,"reference1LocationEnd":2.4,"length1":1.2,"crackWidth":0.3,"offlineDeleted":0}],
  "attachments": [{"offlineUuid":"t1-att-001","offlineSubjectUuid":"t1-dis-001","name":"裂缝照片1.jpg","type":1,"minioId":76115,"offlineDeleted":0}],
  "biObjectComponents": [{"offlineUuid":"t1-rel-001","objectUuid":"t1-obj-span-001","componentUuid":"t1-comp-001","weight":1.0,"offlineDeleted":0}]
}
```

## test2（更新已有 offlineUuid）

**这个 test 用来干嘛：**
验证“同 offlineUuid 不新增、走更新”。

```json
{
  "syncUuid": "test-sync-case-002",
  "clientInfo": "ApiPost/Test2",
  "buildings": [{"offlineUuid":"t1-bld-001","name":"测试桥梁T1-更新","isLeaf":"1","status":"0","offlineDeleted":0}],
  "objects": [],
  "components": [],
  "diseases": [{"offlineUuid":"t1-dis-001","description":"离线裂缝-更新描述","type":"裂缝","level":1,"quantity":2,"offlineDeleted":0}],
  "diseaseDetails": [],
  "attachments": []
}
```

## test3（offlineDeleted=1 删除已存在）

**这个 test 用来干嘛：**
验证删除同步是否生效（对已有数据执行删除分支）。

**通过标准：**
- component/disease 对应记录进入删除态（看 `del_flag` 或删除结果）

```json
{
  "syncUuid": "test-sync-case-003",
  "clientInfo": "ApiPost/Test3",
  "buildings": [{"offlineUuid":"t1-bld-001","offlineDeleted":1}],
  "objects": [{"offlineUuid":"t1-obj-span-001","offlineDeleted":1},{"offlineUuid":"t1-obj-root-001","offlineDeleted":1}],
  "components": [{"offlineUuid":"t1-comp-001","offlineDeleted":1}],
  "diseases": [{"offlineUuid":"t1-dis-001","offlineDeleted":1}],
  "diseaseDetails": [{"offlineUuid":"t1-dd-001","offlineDeleted":1}],
  "attachments": [{"offlineUuid":"t1-att-001","offlineDeleted":1}]
}
```

## test3-2（附件 offlineDeleted=1 删除语义专项）

**这个 test 用来干嘛：**
明确“附件删除”当前实现行为，避免测试误报。

```json
{
  "syncUuid": "test-sync-case-003-2",
  "clientInfo": "ApiPost/Test3-2",
  "buildings": [],
  "objects": [],
  "components": [],
  "diseases": [],
  "diseaseDetails": [],
  "attachments": [{"offlineUuid":"t8-att-001","offlineDeleted":1}]
}
```

## test4（offlineDeleted=1 + 不存在记录）

**这个 test 用来干嘛：**
验证幂等删除：删不存在数据不应报错。

```json
{
  "syncUuid": "test-sync-case-004",
  "clientInfo": "ApiPost/Test4",
  "buildings": [{"offlineUuid":"not-exist-bld-999","offlineDeleted":1}],
  "objects": [],
  "components": [],
  "diseases": [],
  "diseaseDetails": [],
  "attachments": []
}
```

## test5（类型测试，多组 JSON）

### test5-1：quantity 小数字符串

```json
{
  "syncUuid": "test-sync-case-005-1",
  "clientInfo": "ApiPost/Test5-1",
  "buildings": [],
  "objects": [],
  "components": [],
  "diseases": [{"offlineUuid":"t5-dis-001","type":"裂缝","description":"错误类型测试","quantity":"1.2","offlineDeleted":0}],
  "diseaseDetails": [],
  "attachments": []
}
```

### test5-2：positionNumber 字符串

```json
{
  "syncUuid": "test-sync-case-005-2",
  "clientInfo": "ApiPost/Test5-2",
  "buildings": [],
  "objects": [],
  "components": [],
  "diseases": [{"offlineUuid":"t5-dis-002","type":"裂缝","description":"positionNumber字符串测试","positionNumber":"1","level":1,"quantity":1,"offlineDeleted":0}],
  "diseaseDetails": [],
  "attachments": []
}
```

### test5-3：quantity 负数

```json
{
  "syncUuid": "test-sync-case-005-3",
  "clientInfo": "ApiPost/Test5-3",
  "buildings": [],
  "objects": [],
  "components": [],
  "diseases": [{"offlineUuid":"t5-dis-003","type":"裂缝","description":"负数数量测试","level":1,"quantity":-1,"offlineDeleted":0}],
  "diseaseDetails": [],
  "attachments": []
}
```

### test5-4：biObjectComponents.weight 边界

```json
{
  "syncUuid": "test-sync-case-005-4",
  "clientInfo": "ApiPost/Test5-4",
  "buildings": [{"offlineUuid":"t5-bld-004","name":"桥梁T5-4","isLeaf":"1","status":"0","offlineDeleted":0}],
  "objects": [{"offlineUuid":"t5-obj-004","name":"对象T5-4","buildingUuid":"t5-bld-004","status":"0","offlineDeleted":0}],
  "components": [{"offlineUuid":"t5-comp-004","name":"构件T5-4","code":"T5C04","status":"0","objectUuid":"t5-obj-004","offlineDeleted":0}],
  "diseases": [],
  "diseaseDetails": [],
  "attachments": [],
  "biObjectComponents": [
    {"offlineUuid":"t5-rel-004-a","objectUuid":"t5-obj-004","componentUuid":"t5-comp-004","weight":0,"offlineDeleted":0},
    {"offlineUuid":"t5-rel-004-b","objectUuid":"t5-obj-004","componentUuid":"t5-comp-004","weight":1.5,"offlineDeleted":0}
  ]
}
```

## test6（父子依赖-顺序）

**这个 test 用来干嘛：**
验证对象树父子关系在正常顺序下可正确建立。

```json
{
  "syncUuid": "test-sync-case-006",
  "clientInfo": "ApiPost/Test6",
  "buildings": [{"offlineUuid":"t6-bld-001","name":"测试桥梁T6","isLeaf":"1","status":"0","offlineDeleted":0,"rootObjectUuid":"t6-obj-root-001"}],
  "objects": [
    {"offlineUuid":"t6-obj-root-001","name":"根节点","parentUuid":"","buildingUuid":"t6-bld-001","status":"0","offlineDeleted":0},
    {"offlineUuid":"t6-obj-child-001","name":"子节点","parentUuid":"t6-obj-root-001","buildingUuid":"t6-bld-001","status":"0","offlineDeleted":0}
  ],
  "components": [],
  "diseases": [],
  "diseaseDetails": [],
  "attachments": []
}
```

## test6-2（父子依赖-乱序，多轮处理）

**这个 test 用来干嘛：**
验证子节点先到、父节点后到时，多轮处理仍可建立关系。

```json
{
  "syncUuid": "test-sync-case-006-2",
  "clientInfo": "ApiPost/Test6-2",
  "buildings": [{"offlineUuid":"t62-bld-001","name":"测试桥梁T6-2","isLeaf":"1","status":"0","offlineDeleted":0,"rootObjectUuid":"t62-obj-root-001"}],
  "objects": [
    {"offlineUuid":"t62-obj-child-001","name":"子节点","parentUuid":"t62-obj-root-001","buildingUuid":"t62-bld-001","status":"0","offlineDeleted":0},
    {"offlineUuid":"t62-obj-root-001","name":"根节点","parentUuid":"","buildingUuid":"t62-bld-001","status":"0","offlineDeleted":0}
  ],
  "components": [],
  "diseases": [],
  "diseaseDetails": [],
  "attachments": []
}
```

## test7（跨实体引用链 + 可用于结构查询）

**这个 test 用来干嘛：**
验证 disease 的 building/object/component 引用链映射，以及 building 的 rootObjectId 回填。

```json
{
  "syncUuid": "test-sync-case-007",
  "clientInfo": "ApiPost/Test7",
  "buildings": [{"offlineUuid":"t7-bld-001","name":"桥梁T7","isLeaf":"1","status":"0","offlineDeleted":0,"rootObjectUuid":"t7-obj-root-001"}],
  "objects": [
    {"offlineUuid":"t7-obj-root-001","name":"上部结构","parentUuid":"","buildingUuid":"t7-bld-001","status":"0","offlineDeleted":0},
    {"offlineUuid":"t7-obj-001","name":"对象T7","parentUuid":"t7-obj-root-001","buildingUuid":"t7-bld-001","status":"0","offlineDeleted":0}
  ],
  "components": [{"offlineUuid":"t7-comp-001","name":"构件T7","code":"T7-C01","status":"0","objectUuid":"t7-obj-001","offlineDeleted":0}],
  "diseases": [{"offlineUuid":"t7-dis-001","buildingUuid":"t7-bld-001","objectUuid":"t7-obj-001","componentUuid":"t7-comp-001","type":"锈蚀","description":"链路测试","level":1,"quantity":1,"offlineDeleted":0}],
  "diseaseDetails": [],
  "attachments": []
}
```

## test8（附件正常写入）

**这个 test 用来干嘛：**
验证 `offlineSubjectUuid -> subject_id` 映射。

```json
{
  "syncUuid": "test-sync-case-008",
  "clientInfo": "ApiPost/Test8",
  "buildings": [],
  "objects": [],
  "components": [],
  "diseases": [{"offlineUuid":"t8-dis-001","type":"破损","description":"附件测试病害","level":1,"quantity":1,"offlineDeleted":0}],
  "diseaseDetails": [],
  "attachments": [{"offlineUuid":"t8-att-001","offlineSubjectUuid":"t8-dis-001","name":"t8.jpg","type":1,"minioId":76115,"offlineDeleted":0}]
}
```

## test9（附件孤儿引用）

**这个 test 用来干嘛：**
验证无效 `offlineSubjectUuid` 时系统容错，不崩溃。

```json
{
  "syncUuid": "test-sync-case-009",
  "clientInfo": "ApiPost/Test9",
  "buildings": [],
  "objects": [],
  "components": [],
  "diseases": [],
  "diseaseDetails": [],
  "attachments": [{"offlineUuid":"t9-att-001","offlineSubjectUuid":"not-exist-dis-001","name":"orphan.jpg","type":1,"minioId":76115,"offlineDeleted":0}]
}
```

## test9-2（非附件引用缺失）

**这个 test 用来干嘛：**
验证 component/disease 的外键 UUID 不存在时系统行为（容错或报错）。

```json
{
  "syncUuid": "test-sync-case-009-2",
  "clientInfo": "ApiPost/Test9-2",
  "buildings": [],
  "objects": [],
  "components": [{"offlineUuid":"t92-comp-001","name":"孤儿构件","code":"T92-C01","status":"0","objectUuid":"not-exist-object-001","offlineDeleted":0}],
  "diseases": [{"offlineUuid":"t92-dis-001","buildingUuid":"not-exist-building-001","objectUuid":"not-exist-object-001","componentUuid":"not-exist-component-001","type":"裂缝","description":"外键缺失测试","level":1,"quantity":1,"offlineDeleted":0}],
  "diseaseDetails": [],
  "attachments": []
}
```

## test10（新标 biObjectComponents）

**这个 test 用来干嘛：**
验证 2026 新标关联关系同步。

```json
{
  "syncUuid": "test-sync-case-010",
  "clientInfo": "ApiPost/Test10",
  "buildings": [{"offlineUuid":"t10-bld-001","name":"桥梁T10","isLeaf":"1","status":"0","offlineDeleted":0}],
  "objects": [{"offlineUuid":"t10-obj-001","name":"对象T10","buildingUuid":"t10-bld-001","status":"0","offlineDeleted":0}],
  "components": [{"offlineUuid":"t10-comp-001","name":"构件T10","code":"T10-C01","status":"0","objectUuid":"t10-obj-001","offlineDeleted":0}],
  "diseases": [],
  "diseaseDetails": [],
  "attachments": [],
  "biObjectComponents": [{"offlineUuid":"t10-rel-001","objectUuid":"t10-obj-001","componentUuid":"t10-comp-001","weight":1.0,"offlineDeleted":0}]
}
```

## test11（幂等）

**这个 test 用来干嘛：**
验证重复上传不会产生重复业务数据。

复用 test1 body 连续提交两次，仅改 `syncUuid`：
- `test-sync-case-011-a`
- `test-sync-case-011-b`

## test12（鉴权失败）

**这个 test 用来干嘛：**
验证网关/鉴权拦截是否正确。

```json
{
  "syncUuid": "test-sync-case-012",
  "clientInfo": "ApiPost/Test12",
  "buildings": [],
  "objects": [],
  "components": [],
  "diseases": [],
  "diseaseDetails": [],
  "attachments": []
}
```

## test13（空载荷 / 缺 key）

**这个 test 用来干嘛：**
验证空数据场景稳定性。

### test13-1 全空数组

```json
{
  "syncUuid": "test-sync-case-013-1",
  "clientInfo": "ApiPost/Test13-1",
  "buildings": [],
  "objects": [],
  "components": [],
  "diseases": [],
  "diseaseDetails": [],
  "attachments": [],
  "biObjectComponents": []
}
```

### test13-2 缺失部分 key

```json
{
  "syncUuid": "test-sync-case-013-2",
  "clientInfo": "ApiPost/Test13-2",
  "buildings": [{"offlineUuid":"t13-bld-001","name":"空数组缺失键测试","isLeaf":"1","status":"0","offlineDeleted":0}]
}
```

## test14（可选：SQLite 后置失败分支）

**这个 test 用来干嘛：**
验证“业务同步成功，但用户 SQLite 重建失败”时的接口返回。

可操作方式（任选）：
1. 临时将 MinIO bucket 配置为不存在值并重启
2. 临时阻断 MinIO 网络
3. 测试环境 mock `putObject` 抛异常

预期：`msg` 包含“同步上传成功，但重新打包数据库时发生异常”。

## test15（可选：并发同 UUID 冲突）

**这个 test 用来干嘛：**
验证同一 `offlineUuid` 被并发提交时是否出现重复或脏写。

执行方法：
- 两份请求体使用同 `offlineUuid`，字段值不同
- 并发几乎同时发送

通过标准：
- 不出现重复行
- 最终值可解释
- `bi_sync_log` 两条可追踪

---

## 6. 专项 SQL

### test1 全链路

```sql
SELECT id, name, offline_uuid FROM bi_building WHERE offline_uuid='t1-bld-001';
SELECT id, name, parent_id, building_id, offline_uuid FROM bi_object WHERE offline_uuid IN ('t1-obj-root-001','t1-obj-span-001') ORDER BY id;
SELECT id, name, code, bi_object_id, offline_uuid FROM bi_component WHERE offline_uuid='t1-comp-001';
SELECT id, description, quantity, level, building_id, bi_object_id, component_id, offline_uuid FROM bi_disease WHERE offline_uuid='t1-dis-001';
SELECT id, disease_id, disease_uuid, length1, crack_width, offline_uuid FROM bi_disease_detail WHERE offline_uuid='t1-dd-001';
SELECT id, name, subject_id, minio_id, offline_uuid FROM bi_attachment WHERE offline_uuid='t1-att-001';
SELECT id, offline_uuid, bi_object_id, component_id, weight FROM bi_object_component WHERE offline_uuid='t1-rel-001';
```

### test6 / test6-2 父子关系

```sql
SELECT id, offline_uuid, parent_uuid, parent_id
FROM bi_object
WHERE offline_uuid IN ('t6-obj-root-001','t6-obj-child-001','t62-obj-root-001','t62-obj-child-001')
ORDER BY id;
```

### test7 rootObjectId 校验

```sql
SELECT id, name, offline_uuid, root_object_uuid, root_object_id
FROM bi_building
WHERE offline_uuid='t7-bld-001';
```

### test11 幂等（跨实体）

```sql
SELECT offline_uuid, COUNT(*) cnt FROM bi_building WHERE offline_uuid='t1-bld-001' GROUP BY offline_uuid;
SELECT offline_uuid, COUNT(*) cnt FROM bi_object WHERE offline_uuid IN ('t1-obj-root-001','t1-obj-span-001') GROUP BY offline_uuid;
SELECT offline_uuid, COUNT(*) cnt FROM bi_component WHERE offline_uuid='t1-comp-001' GROUP BY offline_uuid;
SELECT offline_uuid, COUNT(*) cnt FROM bi_disease WHERE offline_uuid='t1-dis-001' GROUP BY offline_uuid;
SELECT offline_uuid, COUNT(*) cnt FROM bi_attachment WHERE offline_uuid='t1-att-001' GROUP BY offline_uuid;
```

### test3 删除

```sql
SELECT id, offline_uuid, del_flag FROM bi_component WHERE offline_uuid='t1-comp-001';
SELECT id, offline_uuid, del_flag FROM bi_disease WHERE offline_uuid='t1-dis-001';
SELECT id, offline_uuid, del_flag FROM bi_attachment WHERE offline_uuid='t1-att-001';
```

---

## 7. 当前实现注意事项（测试报告建议原文记录）

1. `Disease.quantity` 是 `int`，`"1.2"` 会触发 parseInt 异常。
2. 附件 `offlineDeleted=1` 当前实现可能是“跳过新增”，不是标准删除流程。
3. 事务是“方法级事务 + 单条容错记录错误”，并非所有场景严格全量回滚。
4. 若要结构树可查询，Building 需要可回填 `rootObjectId`（上传时应带 `rootObjectUuid` 并提供对应根对象）。
