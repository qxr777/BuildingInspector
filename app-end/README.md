# app-end 样例 SQLite

## 现场离线作业两个阶段的 user.db（2026-10-27，对应 E2E-APP-001 / `AppOfflineWorkflowE2ETest`）

业务边界：**桥梁本体不在现场创建**。ROOT/UNIT 节点、`bi_building`、5230 评定任务均由
云端 Web 建桥/任务下发时产生并随用户离线包下发（下发行带 `server_id`、`is_offline_data=0`）；
现场 App 只在下发的 UNIT 下新增 SPAN/LAYER/PART 以及构件页数据，sync/upload 的
`buildings` 恒为空数组，`bi_building` 不产生任何回写。

| 文件 | 对应时刻 | 增量内容（`is_offline_data=1`，即该批 sync/upload 实际上传的行） |
|------|----------|----------------------------------------------------------------|
| `user_stage1_span_part_added.db` | 第一批同步前 | 2 个评定单元各增补 2 跨（乱序）；单元1第1跨含上部结构 LAYER（γ=0.40）与板 PART（ω=75，propsJson `{"part":"slab"}`），其余三跨为空跨。`objects`=6 行，`buildings` 为空 |
| `user_stage2_assessment_inputs.db` | 第二批同步前（结构行已随第一批同步后的刷新包转为服务端行：server_id 9204~9209 回填、标志位置 0） | element 1（EL-APP-1，object_uuid=appdemo-slab）+ spanElementPart 锚定 1 + EDI 标度2 病害 1（participate_assess=1）+ 病害详情 1（长1.2/宽0.25）+ 病害照片附件 1（预上传双 id）+ 人工判定 EFI=1 + bearing 应设未设幻影件（作用单元1第1跨，上部结构）。该批 `objects` 为空 |

两批同步后由**后端内部触发** calculate（App 不调用）：受损跨 BSCI=70.0/3类、三空跨
100/1类；单元1 式3.4.3 跨间插值 72.5/3类（INTERP）、单元2 等类均值 100/1类（EQUAL）；
全桥 3.4.4 最差单元 72.5/3类（WORST_UNIT）。

## 固定演示身份

- 桥梁 id=9101（offline_uuid=`appdemo-building`，root/new_root 均=9201）；5230 任务 id=9101
- 结构节点：ROOT 9201 / UNIT 9202、9203（云端下发行）；现场增补 9204~9209（第一批同步后分配）
- `disease_type_id=9301` 为云端 5230/EDI 字典类型的演示 id；附件 `minio_id=9401`、
  `thumb_minio_id=9402` 为预上传返回的原图/缩略图演示 id——同步身份键始终是 offline_uuid

## 重新生成

```bash
python3 -I make_demo_user_db.py
```

DDL 逐字摘自生产 `SqliteService`（用户包 14 张表），表结构与 App 端 user.db 一致。
同目录 `common.db`/`user.db` 为更早的历史样例（已被 .gitignore 忽略）。
