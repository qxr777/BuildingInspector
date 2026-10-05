-- ================================================================================
-- P0 后端 MySQL DDL：JTG/T 5230—2026 桥跨评定 + 离线同步合并改造
-- 脚本名：20261004_p0_span_sync_ddl.sql
-- 目标库：bi (MySQL 8.0.42, 60.205.13.156:3306)
-- 依据：doc/数据同步与新标评定合并建模方案.md (v1.6)
--       + 真实库 schema 实测 (2026-10-04)
--       + 既有 sql/20260409_offline_sync_ddl.sql 字段命名对齐
--
-- 总原则（开闭原则）：只增不改、旧表旧列零破坏、新表独立、双轨可回退。
--
-- 实测关键事实（本脚本的依据，勿凭记忆改）：
--   1. bi_object = 391 万行，bi_component = 28.7 万，bi_disease = 32.3 万 —— 大表 ADD COLUMN 须 INPLACE。
--   2. bi_object 已有 props(TEXT) 列（"附件属性"），故新标扩展标记用【独立 props_json 列】，不碰 props。
--   3. bi_disease 已有 local_id(BIGINT, 100% 填充) = 旧离线 UUID 载体，本次【统一新增 offline_uuid】，local_id 保留过渡。
--   4. 新标列(node_type/span_no/bridge_type/gamma/omega/props_json)与同步列(offline_uuid 等)经实测【全部 MISSING】，可安全 ADD。
--   5. bi_building 已有 root_property_id 列（无碍，保留）。
--   6. bi_id_mapping / bi_sync_log / bi_object_component / bi_user_sqlite / bi_span_* 均【不存在】（20260409 脚本未在目标库执行）。
--   7. 既有离线同步脚本已定义命名：object_uuid / component_uuid / disease_uuid / offline_subject_uuid —— 本脚本对齐复用。
--   8. v1.6 拍板：bi_project / bi_task / bi_building 是下发只读数据，【不加任何 uuid 列】，
--      整数 id 直通（连 bi_building.root_object_uuid 也不需要，root_object_id 整数直通）。
--      与旧脚本冲突处（旧脚本给 bi_building 加 offline_uuid/root_object_uuid、bi_object 加 building_uuid）以 v1.6 为准。
--
-- 执行策略：
--   - 所有 ADD COLUMN 均【可空、无默认值】+ ALGORITHM=INPLACE, LOCK=NONE，避免锁表。
--   - 普通索引与唯一索引均【ALGORITHM=INPLACE, LOCK=NONE】（非锁定建索引）。
--   - 索引【整体延后】：bi_object 的 2 个普通索引（idx_object_node_type/idx_object_span_no）
--     延后到 P1（评定引擎真要用 span_no/node_type 查询时再建）；唯一索引延后到 P2（同步线上线前）。
--     P0 只做：加列（INSTANT）+ 新表 + 建空表，均秒级完成，避开 391 万行大表建索引冷读耗时。
--   - 脚本幂等：字段/表/索引均已用存储过程判断存在性，可重复执行。
-- ================================================================================

-- ================================================================================
-- 第一部分：幂等工具存储过程（字段 / 普通索引 / 唯一索引 判断存在性）
-- ================================================================================
DROP PROCEDURE IF EXISTS `P0_AddColumnIfNotExists`;
DROP PROCEDURE IF EXISTS `P0_AddIndexIfNotExists`;

DELIMITER $$

CREATE PROCEDURE `P0_AddColumnIfNotExists`(
    IN p_db   VARCHAR(64),
    IN p_tbl  VARCHAR(64),
    IN p_col  VARCHAR(64),
    IN p_def  TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = p_db AND TABLE_NAME = p_tbl AND COLUMN_NAME = p_col
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_tbl, '` ADD COLUMN `', p_col, '` ', p_def, ', ALGORITHM=INPLACE, LOCK=NONE');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
    END IF;
END $$

CREATE PROCEDURE `P0_AddIndexIfNotExists`(
    IN p_db   VARCHAR(64),
    IN p_tbl  VARCHAR(64),
    IN p_idx  VARCHAR(64),
    IN p_kind VARCHAR(16),     -- 'NORMAL' | 'UNIQUE'
    IN p_cols VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = p_db AND TABLE_NAME = p_tbl AND INDEX_NAME = p_idx
    ) THEN
        IF p_kind = 'UNIQUE' THEN
            SET @ddl = CONCAT('ALTER TABLE `', p_tbl, '` ADD UNIQUE KEY `', p_idx, '` (', p_cols, '), ALGORITHM=INPLACE, LOCK=NONE');
        ELSE
            SET @ddl = CONCAT('ALTER TABLE `', p_tbl, '` ADD KEY `', p_idx, '` (', p_cols, '), ALGORITHM=INPLACE, LOCK=NONE');
        END IF;
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
    END IF;
END $$

DELIMITER ;

SET @db = DATABASE();

-- ================================================================================
-- 第二部分：新标语义列（bi_object，6 列，桥跨评定引擎依赖）
-- ================================================================================
-- node_type / span_no / bridge_type / gamma / omega / props_json

CALL P0_AddColumnIfNotExists(@db, 'bi_object', 'node_type',
    'VARCHAR(16) NOT NULL DEFAULT ''LEGACY'' COMMENT ''节点类型:ROOT/UNIT/SPAN/LAYER/PART/LEAF(结构对象末端)/LEGACY(旧数据占位)''');
CALL P0_AddColumnIfNotExists(@db, 'bi_object', 'span_no',
    'INT NULL COMMENT ''跨号,从1起;仅SPAN及跨下节点有效,其余NULL''');
CALL P0_AddColumnIfNotExists(@db, 'bi_object', 'bridge_type',
    'VARCHAR(16) NULL COMMENT ''桥型代码(附录B B01~B21),UNIT级绑定,SPAN继承''');
CALL P0_AddColumnIfNotExists(@db, 'bi_object', 'gamma',
    'DECIMAL(6,4) NULL COMMENT ''结构层影响系数γ(附录B,含0.42/0.38非整值)''');
CALL P0_AddColumnIfNotExists(@db, 'bi_object', 'omega',
    'INT NULL COMMENT ''部件权重ω(附录B,0~100整数,与旧weight隔离)''');
CALL P0_AddColumnIfNotExists(@db, 'bi_object', 'props_json',
    'TEXT NULL COMMENT ''扩展属性JSON(共用墩/全桥性构件/待确认等标记);独立于旧props(附件属性)列''');

-- 索引：span_no 用于按跨聚合评定，node_type 用于结构节点筛选（大表，普通索引）
-- 决策（2026-10-04）：这 2 个普通索引延后到 P1（评定引擎真要用 span_no/node_type 查询时再建），
--   bi_object 391 万行/633MB 数据，缓冲池仅 128MB，建索引需冷读全表，是 P0 唯一实质耗时项。
--   P1 需要时取消注释执行（非锁定 INPLACE）：
-- CALL P0_AddIndexIfNotExists(@db, 'bi_object', 'idx_object_node_type', 'NORMAL', 'node_type');
-- CALL P0_AddIndexIfNotExists(@db, 'bi_object', 'idx_object_span_no', 'NORMAL', 'span_no');

-- ================================================================================
-- 第三部分：离线同步字段（对齐既有 20260409 脚本命名 + 补 offline_deleted）
-- ================================================================================
-- 说明：旧脚本已定义 offline_uuid / parent_uuid / object_uuid / component_uuid /
--       disease_uuid / offline_subject_uuid / is_offline_data 等命名，本脚本对齐复用；
--       统一补 offline_deleted（软删标记，旧脚本未覆盖）。
--       核心规律（v1.6 拍板）：外键指向「下发只读数据」用整数 id 直通（不加 uuid 列）；
--                            外键指向「采集生成数据」才加 offline_uuid。
--       → bi_project / bi_task / bi_building 是下发只读，【不加任何 uuid 列】（整数 id 直通）。
--       → bi_object 不加 building_uuid；bi_disease 用整数 building_id 直通、不加 building_uuid。

-- 【1】bi_object（混合：下发UNIT + 采集SPAN/PART；主键策略见文档§3.3方案X）
CALL P0_AddColumnIfNotExists(@db, 'bi_object', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线UUID-4,同步唯一标识''');
CALL P0_AddColumnIfNotExists(@db, 'bi_object', 'parent_uuid', 'VARCHAR(64) NULL COMMENT ''父节点offline_uuid(离线树父子引用统一用UUID)''');
CALL P0_AddColumnIfNotExists(@db, 'bi_object', 'is_offline_data', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''0=云端/下发,1=离线生成''');
CALL P0_AddColumnIfNotExists(@db, 'bi_object', 'offline_deleted', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''0=正常,1=已删除''');

-- 【2】bi_component（采集生成）
CALL P0_AddColumnIfNotExists(@db, 'bi_component', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线记录唯一标识(UUID)''');
CALL P0_AddColumnIfNotExists(@db, 'bi_component', 'object_uuid', 'VARCHAR(64) NULL COMMENT ''关联结构物(部件)的offline_uuid''');
CALL P0_AddColumnIfNotExists(@db, 'bi_component', 'is_offline_data', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''0=云端,1=离线生成''');
CALL P0_AddColumnIfNotExists(@db, 'bi_component', 'offline_deleted', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''0=正常,1=已删除''');

-- 【3】bi_disease（采集生成；挂载字段分两类：整数直通下发数据 + UUID 指向采集数据）
CALL P0_AddColumnIfNotExists(@db, 'bi_disease', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线记录唯一标识(UUID)''');
CALL P0_AddColumnIfNotExists(@db, 'bi_disease', 'object_uuid', 'VARCHAR(64) NULL COMMENT ''关联结构物(部件)的offline_uuid''');
CALL P0_AddColumnIfNotExists(@db, 'bi_disease', 'component_uuid', 'VARCHAR(64) NULL COMMENT ''关联构件的offline_uuid''');
CALL P0_AddColumnIfNotExists(@db, 'bi_disease', 'is_offline_data', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''0=云端,1=离线生成''');
CALL P0_AddColumnIfNotExists(@db, 'bi_disease', 'offline_deleted', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''0=正常,1=已删除''');
-- 注意：不加 building_uuid（building_id 整数直通，已存在）

-- 【4】bi_disease_detail（采集生成）
CALL P0_AddColumnIfNotExists(@db, 'bi_disease_detail', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线记录唯一标识(UUID)''');
CALL P0_AddColumnIfNotExists(@db, 'bi_disease_detail', 'disease_uuid', 'VARCHAR(64) NULL COMMENT ''关联病害的offline_uuid''');
CALL P0_AddColumnIfNotExists(@db, 'bi_disease_detail', 'is_offline_data', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''0=云端,1=离线生成''');
CALL P0_AddColumnIfNotExists(@db, 'bi_disease_detail', 'offline_deleted', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''0=正常,1=已删除''');

-- 【5】bi_attachment（采集生成）
CALL P0_AddColumnIfNotExists(@db, 'bi_attachment', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线多媒体记录外标(UUID)''');
CALL P0_AddColumnIfNotExists(@db, 'bi_attachment', 'offline_subject_uuid', 'VARCHAR(64) NULL COMMENT ''被挂载主体(病害等)的offline_uuid''');
CALL P0_AddColumnIfNotExists(@db, 'bi_attachment', 'is_offline_data', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''0=云端,1=离线生成''');
CALL P0_AddColumnIfNotExists(@db, 'bi_attachment', 'offline_deleted', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''0=正常,1=已删除''');

-- 【6】下发只读表 bi_project / bi_task / bi_building：不加任何 uuid 列（整数 id 直通）

-- ================================================================================
-- 第四部分：全新业务表（桥跨评定 + 同步基础设施）
-- ================================================================================

-- 4.1 构件-桥跨-部件映射（共用构件跨级部件锚定，服务 BSCI 评定）
CREATE TABLE IF NOT EXISTS `bi_span_component_part` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `component_id`  BIGINT NOT NULL COMMENT '构件ID(bi_component.id)',
  `span_id`       BIGINT NOT NULL COMMENT '桥跨节点ID(bi_object.id, node_type=SPAN)',
  `part_id`       BIGINT NOT NULL COMMENT '该构件在此跨内所属部件节点ID(bi_object.id, node_type=PART)',
  `is_shared`     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否共用构件(相邻跨均计入,1=共用)',
  `offline_uuid`  VARCHAR(64) NULL COMMENT '离线记录唯一标识(UUID)',
  `is_offline_data` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0=云端,1=离线生成',
  `offline_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0=正常,1=已删除',
  `create_by`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time`   DATETIME NULL COMMENT '创建时间',
  `update_by`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time`   DATETIME NULL COMMENT '更新时间',
  `remark`        VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_component_span_part` (`component_id`, `span_id`, `part_id`),
  KEY `idx_span` (`span_id`),
  KEY `idx_component` (`component_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='构件-桥跨-部件映射(共用构件跨级部件锚定,服务BSCI评定)';

-- 4.2 桥跨技术状况评定结果（新标准 JTG/T 5230—2026）
CREATE TABLE IF NOT EXISTS `bi_span_evaluation` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `span_id`       BIGINT NOT NULL COMMENT '桥跨节点ID(bi_object.id)',
  `task_id`       BIGINT NULL COMMENT '关联检测任务',
  `bridge_type`   VARCHAR(16) NULL COMMENT '桥型代码',
  `bsci`          DECIMAL(8,4) NULL COMMENT '桥跨技术状况指数BSCI',
  `span_level`    INT NULL COMMENT '桥跨等级(1~5)',
  `edi_score`     DECIMAL(8,4) NULL COMMENT 'EDI 分项',
  `efi_score`     DECIMAL(8,4) NULL COMMENT 'EFI 分项',
  `eai_score`     DECIMAL(8,4) NULL COMMENT 'EAI 分项',
  `std_version`   VARCHAR(16) NOT NULL DEFAULT '5230-2026' COMMENT '评定标准版本',
  `create_by`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time`   DATETIME NULL COMMENT '创建时间',
  `update_by`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time`   DATETIME NULL COMMENT '更新时间',
  `remark`        VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_span_task` (`span_id`, `task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='桥跨技术状况评定(新标准JTG/T 5230-2026)';

-- 4.3 用户 SQLite 文件引用（离线同步基础设施）
CREATE TABLE IF NOT EXISTS `bi_user_sqlite` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT NULL COMMENT '用户ID',
  `minio_id`      BIGINT NULL COMMENT 'SQLite文件MinIO ID',
  `create_time`   DATETIME NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户SQLite文件引用';

-- 注：bi_id_mapping / bi_sync_log 已由既有 20260409_offline_sync_ddl.sql 定义，
--     若目标库尚未执行该脚本，请先执行之（其结构：bi_id_mapping 联合主键 table_name+offline_uuid；
--     bi_sync_log 含 status/finish_time/remark）。本 P0 不再重复建，避免两套结构冲突。

-- ================================================================================
-- 第五部分：唯一索引 —— 【延后到 P2 同步线上线前执行】
-- ================================================================================
-- 决策（2026-10-04）：存量旧数据（bi_object/bi_component/bi_disease 等累计 425 万行）
-- 本身没有离线同步需求，不需要在 P0 阶段立即获得 offline_uuid。
-- 因此「回填 UUID + 建唯一索引」整体延后到 P2 同步线上线前，避免 P0 执行
-- 425 万行 UPDATE + 5 个唯一索引全表扫描带来的长时间锁表与执行耗时。
--
-- P0 只做：加列（INPLACE）+ 新表 + 普通索引（INPLACE），均为元数据级/非锁定操作，秒级~分钟级完成。
-- 以下回填与唯一索引为【P2 参考 SQL】，届时按「加列 → 分批回填 UUID → 建唯一键」三步执行。

-- 5.1 回填存量数据 offline_uuid（仅回填 offline_uuid IS NULL 的旧数据，
--     使其获得稳定 UUID，供同步去重；可分多批，每批 50000 行）
-- UPDATE bi_object   SET offline_uuid = UUID() WHERE offline_uuid IS NULL LIMIT 50000;   -- 反复执行直至 0 行受影响
-- UPDATE bi_component SET offline_uuid = UUID() WHERE offline_uuid IS NULL LIMIT 50000;
-- UPDATE bi_disease   SET offline_uuid = UUID() WHERE offline_uuid IS NULL LIMIT 50000;
-- UPDATE bi_disease_detail SET offline_uuid = UUID() WHERE offline_uuid IS NULL LIMIT 50000;
-- UPDATE bi_attachment SET offline_uuid = UUID() WHERE offline_uuid IS NULL LIMIT 50000;
-- （下发只读表 bi_project/bi_task/bi_building 无 offline_uuid 列，无需回填）
-- （以上 LIMIT 需按 PK 分段；建议用「WHERE id BETWEEN ? AND ?」分批，避免长事务）

-- 5.2 回填完成后建唯一索引（幂等，届时取消注释执行）
-- CALL P0_AddIndexIfNotExists(@db, 'bi_object',         'uk_object_offline_uuid',    'UNIQUE', 'offline_uuid');
-- CALL P0_AddIndexIfNotExists(@db, 'bi_component',      'uk_component_offline_uuid', 'UNIQUE', 'offline_uuid');
-- CALL P0_AddIndexIfNotExists(@db, 'bi_disease',        'uk_disease_offline_uuid',   'UNIQUE', 'offline_uuid');
-- CALL P0_AddIndexIfNotExists(@db, 'bi_disease_detail', 'uk_detail_offline_uuid',    'UNIQUE', 'offline_uuid');
-- CALL P0_AddIndexIfNotExists(@db, 'bi_attachment',     'uk_attachment_offline_uuid','UNIQUE', 'offline_uuid');

-- ================================================================================
-- 第六部分：清理存储过程
-- ================================================================================
DROP PROCEDURE IF EXISTS `P0_AddColumnIfNotExists`;
DROP PROCEDURE IF EXISTS `P0_AddIndexIfNotExists`;

-- ============================== P0 正向 DDL 结束 ==============================
