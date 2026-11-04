-- =============================================================================
-- MinIO 孤儿文件对账 SQL（对账第 2~3 步）
-- 配套：sql/tools/minio_orphan_inventory.sh 导出的 TSV 清单
--
-- 运行方式（TSV 路径占位符 __INVENTORY_TSV__ 用 sed 替换）：
--   sed "s#__INVENTORY_TSV__#/tmp/minio_inventory.tsv#" sql/tools/minio_orphan_audit.sql \
--     | mysql --local-infile=1 -h <db_host> -u root -p bi
--
-- 只统计某日期之前的对象（如 2026 年前）：再替换一行
--   | sed "s/SET @cutoff := NULL;/SET @cutoff := '2026-01-01';/"
--
-- 输出 7 个结果集：
--   refs_loaded: 实际装载的引用行数（用于确认引用源是否生效）
--   ⓪ 总览
--   ① 孤儿对象（MinIO 有、bi_file_map 无）按扩展名汇总
--   ② 孤儿对象明细（默认最大 200 个；导全量去掉 LIMIT）
--   ③ 死记录（bi_file_map 有、对象已丢，下载必 404；refs 为 NULL 才可删行）
--   ④ 可回收的库里文件（对象存在但无任何业务引用）汇总
--   ⑤ ④的明细（核对属主与生成清理语句用）
--
-- 兼容性：11 个引用源按 information_schema 动态检测，库中缺失的表/列自动跳过
-- （2026-10-31 实测生产 bi 库缺 bi_user_sqlite/bi_building_package/
-- bi_file_upload_registry，仅 7 张表存在，脚本正常执行）。
-- 要求 MySQL 8.0；TEMPORARY 表在单条语句中只能引用一次，故总览为单表聚合。
-- =============================================================================

-- null=不限时间；'2026-01-01'=只看 2026 年前
SET @cutoff := NULL;

-- ---------------------------------------------------------------------------
-- 载入 MinIO 清单
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_minio_inventory;
CREATE TEMPORARY TABLE tmp_minio_inventory (
  object_name   VARCHAR(255) NOT NULL,
  size_bytes    BIGINT       DEFAULT NULL,
  last_modified DATETIME     DEFAULT NULL,
  PRIMARY KEY (object_name),
  KEY idx_inv_time (last_modified)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

LOAD DATA LOCAL INFILE '__INVENTORY_TSV__'
INTO TABLE tmp_minio_inventory
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(object_name, size_bytes, @ts)
SET last_modified = LEFT(REPLACE(@ts, 'T', ' '), 19);

-- ---------------------------------------------------------------------------
-- 汇总所有引用 bi_file_map.id 的业务列（共 9 列）
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_file_refs;
CREATE TEMPORARY TABLE tmp_file_refs (
  file_map_id BIGINT NOT NULL,
  src         VARCHAR(64) NOT NULL,
  ref_time    DATETIME DEFAULT NULL,
  KEY idx_ref_id (file_map_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 动态汇总：只纳入"当前库里实际存在的表+列"。生产库可能缺表（例如
-- bi_user_sqlite/bi_building_package/bi_file_upload_registry 的 DDL 尚未执行），
-- 硬编码查询会 1146 报错，故按 information_schema 生成 UNION ALL 语句。
SET SESSION group_concat_max_len = 102400;
SELECT GROUP_CONCAT(stmt SEPARATOR ' UNION ALL ') INTO @refs_sql FROM (
  SELECT CONCAT(
    'SELECT `', col, '` AS file_map_id, ''', label, ''' AS src, ',
    IF(time_col IS NULL, 'NULL', CONCAT('`', time_col, '`')), ' AS ref_time',
    ' FROM `', tbl, '` WHERE `', col, '` IS NOT NULL') AS stmt
  FROM (
    SELECT 'bi_app_package'           AS tbl, 'minio_id'          AS col, 'bi_app_package'        AS label, NULL          AS time_col
    UNION ALL SELECT 'bi_attachment',         'minio_id',                'bi_attachment(原图)',   NULL
    UNION ALL SELECT 'bi_attachment',         'thumb_minio_id',          'bi_attachment(缩略图)', NULL
    UNION ALL SELECT 'bi_package',            'minio_id',                'bi_package',            NULL
    UNION ALL SELECT 'bi_report',             'minio_id',                'bi_report',             NULL
    UNION ALL SELECT 'bi_report_template',    'minio_id',                'bi_report_template',    NULL
    UNION ALL SELECT 'bi_task_sheets',         'sheet_minioId',           'bi_task_sheets',        NULL
    UNION ALL SELECT 'bi_user_sqlite',        'minio_id',                'bi_user_sqlite',        NULL
    UNION ALL SELECT 'bi_building_package',   'minio_id',                'bi_building_package',   NULL
    UNION ALL SELECT 'bi_file_upload_registry','file_map_id',            'registry(原图)',        'create_time'
    UNION ALL SELECT 'bi_file_upload_registry','thumb_file_map_id',       'registry(缩略图)',      'create_time'
  ) specs
  WHERE EXISTS (SELECT 1 FROM information_schema.columns c
                WHERE c.table_schema = DATABASE()
                  AND c.table_name   = specs.tbl
                  AND c.column_name  = specs.col)
) present;

SET @sql := IF(@refs_sql IS NULL, 'SELECT 1',
               CONCAT('INSERT INTO tmp_file_refs (file_map_id, src, ref_time) ', @refs_sql));
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;
SELECT COUNT(*) AS refs_loaded FROM tmp_file_refs;

-- ---------------------------------------------------------------------------
-- 给每个 bi_file_map 行打引用标记
--   has_business_ref: 9 个业务列中是否有引用
--   has_any_ref:      含预上传登记表在内是否有任何引用
--   recent_registry:  仅被登记表引用且近 30 天有登记（在途上传，宽限排除）
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_file_class;
CREATE TEMPORARY TABLE tmp_file_class (
  id               INT NOT NULL,
  newName          VARCHAR(255),
  has_business_ref TINYINT NOT NULL,
  has_any_ref      TINYINT NOT NULL,
  recent_registry  TINYINT NOT NULL,
  refs             VARCHAR(1024),
  PRIMARY KEY (id),
  KEY idx_class_name (newName)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET SESSION group_concat_max_len = 102400;
INSERT INTO tmp_file_class
SELECT m.id, m.newName,
       MAX(CASE WHEN r.src NOT LIKE 'registry%' THEN 1 ELSE 0 END) AS has_business_ref,
       MAX(CASE WHEN r.src IS NOT NULL THEN 1 ELSE 0 END)          AS has_any_ref,
       MAX(CASE WHEN r.src LIKE 'registry%' AND r.ref_time > NOW() - INTERVAL 30 DAY
                THEN 1 ELSE 0 END)                                 AS recent_registry,
       GROUP_CONCAT(DISTINCT r.src ORDER BY r.src SEPARATOR ',')   AS refs
FROM bi_file_map m
LEFT JOIN tmp_file_refs r ON r.file_map_id = m.id
GROUP BY m.id, m.newName;

-- ⓪ 总览（注意：TEMPORARY 表在一条语句中只能引用一次，故用单表聚合后笛卡尔拼接）
SELECT inv.minio_objects,
       ROUND(inv.minio_bytes/1048576, 1) AS minio_mb,
       fm.file_map_rows,
       fc.map_rows_unreferenced
FROM (
  SELECT SUM(CASE WHEN (@cutoff IS NULL OR last_modified < @cutoff) THEN 1 ELSE 0 END) AS minio_objects,
         SUM(CASE WHEN (@cutoff IS NULL OR last_modified < @cutoff)
                  THEN IFNULL(size_bytes,0) ELSE 0 END)                                AS minio_bytes
  FROM tmp_minio_inventory
) inv,
(SELECT COUNT(*) AS file_map_rows FROM bi_file_map) fm,
(SELECT COUNT(*) AS map_rows_unreferenced FROM tmp_file_class WHERE has_any_ref = 0) fc;

-- ① 孤儿对象（MinIO 有、bi_file_map 没有），按扩展名汇总 -----------------------
SELECT LOWER(SUBSTRING_INDEX(i.object_name, '.', -1)) AS ext,
       COUNT(*)                                       AS cnt,
       ROUND(SUM(i.size_bytes)/1048576, 1)            AS mb
FROM tmp_minio_inventory i
LEFT JOIN bi_file_map m ON m.newName = i.object_name
WHERE m.id IS NULL
  AND (@cutoff IS NULL OR i.last_modified < @cutoff)
GROUP BY ext
ORDER BY mb DESC;

-- ② 孤儿对象明细（先看最大的 200 个；导全量供核对请去掉 LIMIT） ---------------
SELECT i.object_name,
       i.size_bytes,
       i.last_modified
FROM tmp_minio_inventory i
LEFT JOIN bi_file_map m ON m.newName = i.object_name
WHERE m.id IS NULL
  AND (@cutoff IS NULL OR i.last_modified < @cutoff)
ORDER BY i.size_bytes DESC
LIMIT 200;

-- ③ 死记录（bi_file_map 有、MinIO 对象已丢失） --------------------------------
SELECT c.id, c.newName, c.refs
FROM tmp_file_class c
LEFT JOIN tmp_minio_inventory i ON i.object_name = c.newName
WHERE i.object_name IS NULL
ORDER BY c.id;

-- ④ 可回收的库里文件（对象存在 + 无业务引用）汇总 ------------------------------
-- UNREFERENCED  : 无任何引用（含登记表）
-- REGISTRY_STALE: 仅预上传登记表引用且超过 30 天（App 传了但从未挂到附件）
SELECT CASE WHEN c.has_any_ref = 0 THEN 'UNREFERENCED'
            WHEN c.has_business_ref = 0 AND c.recent_registry = 0 THEN 'REGISTRY_STALE'
       END                              AS kind,
       COUNT(*)                         AS cnt,
       ROUND(SUM(i.size_bytes)/1048576,1) AS mb
FROM tmp_file_class c
JOIN tmp_minio_inventory i ON i.object_name = c.newName
WHERE c.has_business_ref = 0
  AND (c.has_any_ref = 0 OR c.recent_registry = 0)
  AND (@cutoff IS NULL OR i.last_modified < @cutoff)
GROUP BY kind
ORDER BY mb DESC;

-- ⑤ ④的明细（隔离/回收操作清单以此为准） ---------------------------------------
SELECT c.id,
       c.newName,
       i.size_bytes,
       i.last_modified,
       CASE WHEN c.has_any_ref = 0 THEN 'UNREFERENCED' ELSE 'REGISTRY_STALE' END AS kind,
       c.refs
FROM tmp_file_class c
JOIN tmp_minio_inventory i ON i.object_name = c.newName
WHERE c.has_business_ref = 0
  AND (c.has_any_ref = 0 OR c.recent_registry = 0)
  AND (@cutoff IS NULL OR i.last_modified < @cutoff)
ORDER BY i.size_bytes DESC;

-- 清理语句生成（默认注释；人工复核⑤后再启用；物理删除对象后于同一维护窗口执行）：
-- SELECT CONCAT('DELETE FROM bi_file_map WHERE id = ', c.id, '; -- ', c.newName) AS sql_stmt
-- FROM tmp_file_class c JOIN tmp_minio_inventory i ON i.object_name = c.newName
-- WHERE c.has_business_ref = 0 AND (c.has_any_ref = 0 OR c.recent_registry = 0)
-- ORDER BY c.id;
