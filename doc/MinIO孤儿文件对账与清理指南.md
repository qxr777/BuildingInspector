# MinIO 孤儿文件对账与清理指南

> 文档版本：v1.0
> 最后更新：2026-10-31
> 适用对象：后端开发 / 运维（DBA、MinIO 管理员）
> 配套脚本：`sql/tools/minio_orphan_inventory.sh`、`sql/tools/minio_orphan_audit.sql`

> **本指南要解决的问题**
> MinIO `public` 桶长期运行后积累"系统查不到、磁盘删不得"的对象：业务表无引用、
> 甚至 `bi_file_map` 都没有记录。本指南给出一套**可重复执行、先隔离后删除、可回滚**
> 的对账与清理流程，并同时反向排查"库里有记录、对象已丢失"的死链。

---

## 1. 背景与登记模型（对账的事实依据）

1. 本后端写入 MinIO 的**所有对象都在 `bi_file_map` 登记**，对象 key 固定为
   `public/<newName前两位>/<newName>`（如 `public/57/5754848d….docx`），
   即 `objectKey = newName.substring(0,2) + "/" + newName`
   （`FileMapServiceImpl#toMinioObjectKey`，约 660 行；唯一绕过 service 的
   `SqliteService#uploadToMinio` 也是先 putObject 再 insertFileMap）。
2. **11 个列引用 `bi_file_map.id`**，对账时必须全部覆盖：

   | 表 | 列 |
   |---|---|
   | bi_app_package | minio_id |
   | bi_attachment | minio_id、thumb_minio_id |
   | bi_package | minio_id |
   | bi_report | minio_id |
   | bi_report_template | minio_id |
   | bi_task_sheets | sheet_minioId |
   | bi_user_sqlite | minio_id |
   | bi_building_package | minio_id |
   | bi_file_upload_registry | file_map_id、thumb_file_map_id（all_in_one 第八·十部分，老库可能不存在，脚本自动跳过） |

   审计脚本对上述 11 个引用源**按 information_schema 动态检测**，库中缺失的表/列
   自动跳过。2026-10-31 实测生产 `bi` 库仅有前 7 张表（`bi_user_sqlite`、
   `bi_building_package`、`bi_file_upload_registry` 均不存在，对应 DDL 尚未在生产执行），
   脚本正常运行，结果集中不会出现 `REGISTRY_STALE` 类。

3. **明确不属于本系统对账范围、不得误删的对象：**
   - **springai_Rag 知识库服务**（`:8081`）可能直连 MinIO 上传知识库文档（如排查中
     发现的 16 个无登记 `.docx`），不经本后端、不写 `bi_file_map`，其元数据在 RAG
     服务自己的库 / chroma 中。删除前必须到 RAG 侧反查确认。
   - 用户头像存本地磁盘（`SysProfileController#updateAvatar` 走 `FileUploadUtils`），
     **不进 MinIO**。
   - 大 ZIP 走阿里云 OSS 桶 `bi-upload-large`，**不是 MinIO**。
4. 部署为**单盘 FS 模式**（对象原名直接落盘），因此除 `mc` 外还可用文件系统方式导清单；
   若将来改为 erasure 模式，fs 方式不再适用（对象变成 `name/xl.meta`+`part.*`）。

---

## 2. 流程总览

```
①导清单(mc 或 fs) → ②清单入临时表 → ③审计 SQL 出 6 个结果集
→ ④逐群确认外部属主（RAG 等）→ ⑤隔离(mc mv，观察 2~4 周) → ⑥物理删除 + 同窗口清库
```

**铁律：任何一步只产出"清单"，不直接删除；删除一律走"隔离 → 观察期 → 物理删"。**

---

## 3. 前置条件

- 一台能装/已有 `mc`（MinIO Client）与 `jq` 的机器；或 MinIO 宿主机的 SSH 权限。
  配置 alias（凭据以 `application.yml` 的 `minio` 段为准，勿写入仓库）：

  ```bash
  mc alias set local http://<minio_host>:9000 <access-key> <secret-key>
  ```

- 能连业务库 `bi` 的 MySQL 8.0 客户端，且允许 `LOAD DATA LOCAL INFILE`
  （命令行加 `--local-infile=1`；服务端 `local_infile=ON`）。
  无客户端时可用 Navicat/DataGrip 导入 TSV 后手工执行审计 SQL 的建表之后部分。
- **执行前备份**：`mysqldump -t bi bi_file_map bi_file_upload_registry > /tmp/file_map_backup_$(date +%F).sql`
  （只导数据；删除操作当天再做一次。）

---

## 4. 操作步骤

### 4.1 第 1 步：导出 MinIO 全量清单

```bash
# 方式 A（推荐，任何机器可跑，元数据最准）
bash sql/tools/minio_orphan_inventory.sh mc  local/public /tmp/minio_inventory.tsv

# 方式 B（在 MinIO 宿主机上，按文件 mtime；需要权限时自行加 sudo）
bash sql/tools/minio_orphan_inventory.sh fs  /home/docker/data/minio/data/public /tmp/minio_inventory.tsv
```

产物 `/tmp/minio_inventory.tsv`：三列 `对象名⇥字节数⇥修改时间`，已去掉 `xx/` 分片
目录、已排除 `.minio.sys` 与 `orphan-quarantine/` 隔离前缀。两种方式建议各跑一次，
对象数应一致，可互相验证。

> 注意：未完成的分片上传不在 `mc ls` 输出中（位于 `.minio.sys/multipart`），
> 如需统计另用 `mc admin` 排查，不属于本流程。

### 4.2 第 2~3 步：入库并执行审计

```bash
# 全量对账
sed "s#__INVENTORY_TSV__#/tmp/minio_inventory.tsv#" sql/tools/minio_orphan_audit.sql \
  | mysql --local-infile=1 -h <db_host> -u root -p bi

# 只看 2026 年前的对象（可据此先清历史存量）
sed -e "s#__INVENTORY_TSV__#/tmp/minio_inventory.tsv#" \
    -e "s/SET @cutoff := NULL;/SET @cutoff := '2026-01-01';/" \
    sql/tools/minio_orphan_audit.sql \
  | mysql --local-infile=1 -h <db_host> -u root -p bi
```

输出 7 个结果集：

| 结果集 | 含义 | 处置方向 |
|---|---|---|
| refs_loaded | 实际装载的引用行数 | 应 >0；为 0 说明引用源检测异常，先排查 |
| ⓪ 总览 | 对象数/总 MB、`bi_file_map` 行数、无引用行数 | 建立基线，留档 |
| ① 孤儿对象按扩展名汇总 | MinIO 有、**`bi_file_map` 都没有**的对象 | 先按扩展名+时间分群，逐群查外部属主 |
| ② 孤儿对象明细 | 同上，默认最大 200 个 | 人工核对；导全量去掉 SQL 中的 LIMIT |
| ③ 死记录 | 库里有记录、对象已丢 | 查 refs 列：仍被业务引用=真实坏链，修业务；无引用=随④清行 |
| ④ 可回收汇总 | 对象在、但 9 个业务列零引用 | `UNREFERENCED` 优先回收；`REGISTRY_STALE` 次之 |
| ⑤ 可回收明细 | ④的逐行清单（含 id/newName/大小/时间/refs） | 隔离与清库操作清单以此为准 |

`REGISTRY_STALE`：仅被 `bi_file_upload_registry` 引用且最近 30 天无登记——即 App
预上传成功但从未挂到附件（表注释本就将其列为未来 GC 依据）。30 天内的在途上传已自动
宽限排除。

### 4.3 第 4 步：确认外部属主（关键，防误删）

对结果①②中的对象，**先按"命名风格 + 扩展名 + 时间簇"分群**，成片同风格 UUID 文件
通常对应同一来源；对每一群确认属主，而不是逐个看：

1. `.docx/.doc` 等知识库文档群 → 找 RAG（springai_Rag，`:8081`）负责人在其元数据中
   按文件名反查；确认无引用前一律视为"外部属主、本系统不可见"。
2. 系统功能文件群（`.db` 用户 SQLite、`.zip` 建筑包、安装包、报告模板等）→ 拿对象名
   反查 `bi_file_map` 之外是否存在历史备份/手工投放；确认后再决定是否补登记或删除。
3. 把确认属外部属主、需要长期保留的前缀/扩展名记录到本指南 §7 白名单，后续审计直接排除。

> 反向核对小技巧：对某个可疑对象名，先
> `SELECT * FROM bi_file_map WHERE newName='<对象名>';`
> 确无记录后，再去对应外部系统查，不要凭扩展名想当然。

### 4.4 第 5 步：隔离（可回滚，建议按批次）

把**确认无主**的对象移入同桶隔离前缀（同桶 `mc mv` 是服务端元数据操作，秒级、
不产生流量，随时可移回）：

```bash
# orphans_confirmed.txt：经 §4.3 确认的对象名，每行一个（不要带 xx/ 前缀）
BATCH=$(date +%Y%m%d)
while IFS= read -r name; do
  [ -z "$name" ] && continue
  pfx="${name:0:2}"
  mc mv "local/public/$pfx/$name" "local/public/orphan-quarantine/$BATCH/$name"
done < orphans_confirmed.txt
```

对结果④⑤（`bi_file_map` 有行）的对象，隔离期间**先不要删库行**——观察期内若发现
业务 404，把对象移回原位即可恢复，库行仍在。

观察期建议 **2~4 周**，期间关注：
- Web/App 端文件下载、报告生成、头像以外的附件打开是否报错；
- 后端日志中 `NoSuchKey` / `对象不存在` / MinIO 下载异常。

> 隔离前缀 `orphan-quarantine/` 已被导清单脚本自动排除，不会在下一轮审计中重复报孤儿。

### 4.5 第 6 步：物理删除 + 同窗口清库

观察期无异常后：

```bash
# 1) 物理删除对象（不可恢复，确认批次号）
mc rm --recursive --force "local/public/orphan-quarantine/$BATCH/"
```

2) 对结果③（死记录）与④⑤（UNREFERENCED / REGISTRY_STALE）清理数据库行。
在审计 SQL 会话中（临时表仍在）可直接按标记生成语句；跨会话则先重跑审计。
启用审计 SQL 末尾注释掉的 `SELECT CONCAT('DELETE …')` 生成 `bi_file_map` 删除语句，
人工复核后执行，并**先删登记表引用**（该表存在时）：

```sql
-- 以下 :id_list 为结果⑤中确认随对象一起清理的 bi_file_map.id 列表
DELETE FROM bi_file_upload_registry
WHERE file_map_id IN (:id_list) OR thumb_file_map_id IN (:id_list);

DELETE FROM bi_file_map WHERE id IN (:id_list);
```

结果①②的纯孤儿对象没有库行，物理删除即完成，无需清库。
结果③中 **refs 非空**的行是真实坏链（业务还在引用已丢失对象），**不要删行掩盖问题**，
应转业务侧修复（补传/解绑/告知用户）。

3) 删除后重跑 §4.1~4.2，确认对象数下降与回收量吻合、结果集收敛。

### 4.6 没有 mc 时的 fs 模式替代

在 MinIO 宿主机上用目录隔离代替 `mc mv`：

```bash
BATCH=$(date +%Y%m%d)
mkdir -p /home/docker/data/minio/data/public/orphan-quarantine/$BATCH
while IFS= read -r name; do
  pfx="${name:0:2}"
  mv "/home/docker/data/minio/data/public/$pfx/$name" \
     "/home/docker/data/minio/data/public/orphan-quarantine/$BATCH/$name"
done < orphans_confirmed.txt
# 观察期后：rm -rf .../orphan-quarantine/$BATCH
```

---

## 5. 安全规则与回滚

1. **只删隔离观察过的对象**；任何来源的"一键 rm 孤儿"脚本都不允许直接上生产。
2. 每批次保留三份清单：审计结果（①~⑤导出）、`orphans_confirmed.txt`、
   删除时的 `mc ls orphan-quarantine/$BATCH` 输出，与备份一起存档。
3. 回滚方式：观察期内 `mc mv` 移回原 `xx/` 前缀；物理删除后只能依靠备份/重传，
   因此观察期不得短于一个完整业务周期。
4. 库行删除必须在对象物理删除**之后同一维护窗口**；删除前确认当天
   `mysqldump -t bi bi_file_map bi_file_upload_registry` 备份可用。
5. 首次执行建议先套 `@cutoff = '2026-01-01'` 清历史存量，跑顺后再做全量。
6. 并发写入注意：审计期间新上传的对象可能出现"清单里没有 / 刚有引用"的瞬时差异，
   清单与 SQL 间隔尽量短（同一小时内），隔离前对清单再抽查最近 24h 的对象。

---

## 6. 定期执行（建议）

- 每季度跑一次全量对账；每次大版本发布后、或磁盘告警时加跑一次。
- 长期可考虑把 `bi_file_upload_registry.create_time + file_map_id` 的 REGISTRY_STALE
  识别固化成定时 GC（仅告警、出清单，不自动删），本指南流程不变。

---

## 6.1 执行记录

| 批次 | 日期 | 范围 | 隔离数/大小 | 隔离前缀 | 观察期截止 | 库行处理 | 产物目录 |
|---|---|---|---|---|---|---|---|
| 20261031 | 2026-10-31 | 2026 年前无业务引用 docx 重复旧版（每簇保留最新 1 份） | 125 个 / 474.6 MB | `public/orphan-quarantine/20261031/` | **2026-11-28** | 观察期内保留 `bi_file_map` 行 | 执行机 `/tmp/minio-audit-20261031/`（review 清单、校验留痕、mv 日志） |
| 20261031-batch2 | 2026-10-31 | R1 档 2025 年存量双重不可达：A 有行零引用（扣第一批，docx 簇保留最新）+ B 无登记本系统 32hex 命名（jpg/png/zip/txt）；无登记 docx 65、pdf 2 共 171.9MB 挂起待 RAG 确认 | 3,834 个 / 1,763.1 MB（A 1,898/514.1MB，B 1,936/1,249.0MB） | `public/orphan-quarantine/20261031-batch2/` | **2026-11-28** | A 组 1,898 个 id 的 `bi_file_map` 行观察期内保留，到期随对象删除；B 组无库行 | `…/batch2/`（batch2_review.tsv、verify_batch2.sql、mv 日志零失败） |

- 本批 125 个 id 已在执行前实时复查 7 张业务表 8 个引用列均为 0 引用；mv 零失败，
  隔离前缀对象数 125 与清单完全一致。
- **到期（2026-11-28）后**：确认观察期内无下载 404/投诉，执行
  `mc rm --recursive --force local/public/orphan-quarantine/20261031/`，
  再按产物 `batch1_review.tsv` 中 QUARANTINE 行的 file_map_id 批量删 `bi_file_map`
  （本批不含 registry 表，生产库无该表）。
- 下一轮候选：同口径剩余 73 份"各簇最新"（332.3MB）与 44 份单份孤立 docx（226.3MB）
  暂缓；以及结果①~②的无登记对象（jpg/png/zip/pdf 等约 1.4GB，外部属主待确认）。

## 7. 外部属主白名单（每轮审计后更新）

| 模式/前缀/扩展名 | 属主系统 | 确认方式 | 登记日期 |
|---|---|---|---|
| 无登记的知识库 `.docx` 群（如 `5754848d….docx` 等 16 个） | 待确认：springai_Rag(`:8081`) | RAG 元数据按文件名反查 | 2026-10-31 待确认 |
| **有 `bi_file_map` 行但无业务引用**的 `.docx` 群（首轮审计 198 个/807MB） | **已查明：报告生成残留**，非 RAG。`ReportServiceImpl` 每次生成 Word 都 `handleFileUploadFromFile` 落新对象；2025-11-25 20:39~20:42 用户 admin2@znjc_wxdw 对"小王庄桥"3分40秒内重复生成 10 次（id 56391/56393~56401，每个约 32.8MB、合计 327.92MB），报告行后不再引用 | 抽查 oldName/createBy 确认同模式后可按本指南隔离；根治需让重复生成/报告删除时清理旧对象（约 718 行已有删除旧版逻辑，排查失效原因） | 2026-10-31 已查明 |

> 上表未确认前，对应对象**只隔离不删除**，隔离期延长到属主明确答复为止。
> 首轮（2026 年前，2026-10-31 执行）实测：84,323 个对象/139.4GB；孤儿对象 2,008 个/
> 约 1.4GB；UNREFERENCED 2,023 个/988.8MB；死记录 516 行（其中 489 行仍被业务引用，
> 属真实坏链：bi_attachment 原图 220、缩略图 197、bi_report 48、bi_task_sheets 19、
> bi_package 5；仅 27 行 refs 为 NULL 可随清理删行）。明细见当次
> `/tmp/audit_pre2026.out`，生产库引用表仅 7 张（3 张新表 DDL 未执行）。

---

## 附录：证据位置

- 表结构：`sql/bi_schema.sql`（bi_file_map 约 348 行、bi_attachment 约 46 行、
  各业务 minio_id 列）；`sql/test_env_all_in_one.sql` 第八·十部分（bi_file_upload_registry）
- 对象 key 规则：`bi-biz/.../service/impl/FileMapServiceImpl.java`（toMinioObjectKey）
- 直传仍登记：`bi-biz/.../service/impl/SqliteService.java`（uploadToMinio）
- 头像走本地盘：`ruoyi-admin/.../system/SysProfileController.java`（updateAvatar）
- MinIO / OSS 配置：`ruoyi-admin/src/main/resources/application.yml`（bucket `public`；
  OSS 桶 `bi-upload-large`）
