-- ================================================================================
-- 20261006 测试库补齐脚本（独立、幂等、可重复执行）
-- ------------------------------------------------------------------------------
-- 背景：测试库落后代码，运行时报 Unknown column：
--   [1] bi_building.new_root_object_id
--   [2] bi_template_object 的 gamma / omega / category_i / props / weight / impact_factor
-- 这些列本应由 20261005_test_env_all_in_one.sql（第六、第七·五部分）补齐。
--
-- 本脚本把「新规范双树 + 版本隔离 + 新标语义列 + 离线同步字段 + 5 张新表」
-- 一次性补齐（全部 IF NOT EXISTS / AddColumnIfNotExists，可重复跑）。
--
-- ★ 最省事的做法其实是直接重跑 sql/20261005_test_env_all_in_one.sql（同样幂等，
--   且会顺带补齐 B01 模板树种子数据）。本脚本不含种子，适合只补 schema、不动数据。
--
-- 用法：在 MySQL 客户端先 `USE 你的库名;` 再执行本文件（脚本内部用 DATABASE() 取库名）。
-- ================================================================================

-- 兼容：若当前 session 未加载过 P_AddColumnIfNotExists 则定义，结束再清理
DROP PROCEDURE IF EXISTS P_AddColumnIfNotExists;
DELIMITER $$
CREATE PROCEDURE P_AddColumnIfNotExists(
    IN dbName VARCHAR(128), IN tblName VARCHAR(128),
    IN colName VARCHAR(128), IN colDef VARCHAR(256))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = dbName AND TABLE_NAME = tblName AND COLUMN_NAME = colName
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', tblName, '` ADD COLUMN `', colName, '` ', colDef);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

SET @db = DATABASE();

-- ================================================================================
-- A. 5 张新业务表（桥跨评定 + 离线同步基础设施）
-- ================================================================================
CREATE TABLE IF NOT EXISTS `bi_id_mapping` (
  `table_name` varchar(64) NOT NULL COMMENT '数据流经实体映射的基础表名',
  `offline_uuid` varchar(64) NOT NULL COMMENT '移动端或中间层分配的离线主键UUID',
  `server_id` bigint(20) NOT NULL COMMENT '服务端入库后的真实物理主键ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '该映射关系的绑定时间',
  PRIMARY KEY (`table_name`, `offline_uuid`),
  KEY `idx_server_id` (`server_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离线数据上行树状拓扑重解 ID 映射图';

CREATE TABLE IF NOT EXISTS `bi_sync_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审计账本ID',
  `sync_uuid` varchar(64) DEFAULT NULL COMMENT '同步批次UUID',
  `user_id` bigint(20) DEFAULT NULL COMMENT '操作人ID',
  `status` int(11) DEFAULT '0' COMMENT '状态: 0=进行中, 1=成功, 2=失败',
  `client_info` varchar(255) DEFAULT NULL COMMENT '客户端信息(App版本,设备号等)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '同步日志记录时间',
  `finish_time` datetime DEFAULT NULL COMMENT '同步完成时间',
  `remark` text COMMENT '同步详细结果/错误备注',
  PRIMARY KEY (`id`),
  KEY `idx_sync_uuid` (`sync_uuid`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离线数据同步审计日志表';

CREATE TABLE IF NOT EXISTS `bi_span_component_part` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `component_id`  BIGINT NOT NULL COMMENT '构件ID(bi_component.id)',
  `span_id`       BIGINT NOT NULL COMMENT '桥跨节点ID(bi_object.id, node_type=SPAN)',
  `part_id`       BIGINT NOT NULL COMMENT '该构件在此跨内所属部件节点ID(bi_object.id, node_type=PART)',
  `component_uuid` VARCHAR(64) NULL COMMENT '构件离线UUID(bi_component.offline_uuid)',
  `span_uuid`      VARCHAR(64) NULL COMMENT '桥跨离线UUID(bi_object.offline_uuid, node_type=SPAN)',
  `part_uuid`      VARCHAR(64) NULL COMMENT '部件离线UUID(bi_object.offline_uuid, node_type=PART)',
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

CREATE TABLE IF NOT EXISTS `bi_user_sqlite` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT NULL COMMENT '用户ID',
  `minio_id`      BIGINT NULL COMMENT 'SQLite文件MinIO ID',
  `create_time`   DATETIME NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户SQLite文件引用';

-- ================================================================================
-- B. bi_object 新标语义列（6 列）
-- ================================================================================
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'node_type',
    'VARCHAR(16) NULL COMMENT ''节点类型:ROOT/UNIT/SPAN/LAYER/PART/LEAF/LEGACY''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'span_no',
    'INT NULL COMMENT ''跨号,从1起;仅SPAN及跨下节点有效''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'bridge_type',
    'VARCHAR(16) NULL COMMENT ''桥型代码(附录B B01~B21),UNIT级绑定,SPAN继承''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'gamma',
    'DECIMAL(6,4) NULL COMMENT ''结构层影响系数γ(附录B)''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'omega',
    'INT NULL COMMENT ''部件权重ω(附录B,0~100整数)''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'props_json',
    'TEXT NULL COMMENT ''扩展属性JSON(共用墩/全桥性构件/待确认等标记)''');

-- ================================================================================
-- C. 5 张采集表离线同步字段
-- ================================================================================
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线UUID''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'parent_uuid', 'VARCHAR(64) NULL COMMENT ''父节点offline_uuid''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'is_offline_data', 'TINYINT(1) NULL COMMENT ''0=云端,1=离线生成''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'offline_deleted', 'TINYINT(1) NULL COMMENT ''0=正常,1=已删除''');

CALL P_AddColumnIfNotExists(@db, 'bi_component', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线记录唯一标识(UUID)''');
CALL P_AddColumnIfNotExists(@db, 'bi_component', 'object_uuid', 'VARCHAR(64) NULL COMMENT ''关联结构物(部件)的offline_uuid''');
CALL P_AddColumnIfNotExists(@db, 'bi_component', 'is_offline_data', 'TINYINT(1) NULL COMMENT ''0=云端,1=离线生成''');
CALL P_AddColumnIfNotExists(@db, 'bi_component', 'offline_deleted', 'TINYINT(1) NULL COMMENT ''0=正常,1=已删除''');

CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线记录唯一标识(UUID)''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'object_uuid', 'VARCHAR(64) NULL COMMENT ''关联结构物(部件)的offline_uuid''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'component_uuid', 'VARCHAR(64) NULL COMMENT ''关联构件的offline_uuid''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'is_offline_data', 'TINYINT(1) NULL COMMENT ''0=云端,1=离线生成''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'offline_deleted', 'TINYINT(1) NULL COMMENT ''0=正常,1=已删除''');

CALL P_AddColumnIfNotExists(@db, 'bi_disease_detail', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线记录唯一标识(UUID)''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease_detail', 'disease_uuid', 'VARCHAR(64) NULL COMMENT ''关联病害的offline_uuid''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease_detail', 'is_offline_data', 'TINYINT(1) NULL COMMENT ''0=云端,1=离线生成''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease_detail', 'offline_deleted', 'TINYINT(1) NULL COMMENT ''0=正常,1=已删除''');

CALL P_AddColumnIfNotExists(@db, 'bi_attachment', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线多媒体记录外标(UUID)''');
CALL P_AddColumnIfNotExists(@db, 'bi_attachment', 'offline_subject_uuid', 'VARCHAR(64) NULL COMMENT ''被挂载主体的offline_uuid''');
CALL P_AddColumnIfNotExists(@db, 'bi_attachment', 'is_offline_data', 'TINYINT(1) NULL COMMENT ''0=云端,1=离线生成''');
CALL P_AddColumnIfNotExists(@db, 'bi_attachment', 'offline_deleted', 'TINYINT(1) NULL COMMENT ''0=正常,1=已删除''');

-- ================================================================================
-- D. bi_template_object 补齐历史演进列 + 新标列（★ 本次报错核心）
--    类型对齐 domain BiTemplateObject + 历史脚本：
--      weight DECIMAL(10,4) / impact_factor DECIMAL(10,4) / category_i VARCHAR(50)
--      props VARCHAR(500) / gamma DECIMAL(6,4) / omega INT
-- ================================================================================
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'weight',
    'DECIMAL(10,4) NULL COMMENT ''旧标准部件权重(历史兼容)''');
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'impact_factor',
    'DECIMAL(10,4) NULL COMMENT ''旧标准影响系数ƴ(历史兼容)''');
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'category_i',
    'VARCHAR(50) NULL COMMENT ''类别(i)''');
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'props',
    'VARCHAR(500) NULL COMMENT ''附加属性(JSON,存stdVersion/bridgeType)''');
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'gamma',
    'DECIMAL(6,4) NULL COMMENT ''结构层影响系数γ(附录B按桥型,与bi_object.gamma对齐)''');
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'omega',
    'INT NULL COMMENT ''部件权重ω(附录B,0~100整数,与bi_object.omega对齐)''');

-- ================================================================================
-- E. bi_building 离线标识 + 新规范双树根节点
-- ================================================================================
CALL P_AddColumnIfNotExists(@db, 'bi_building', 'offline_uuid',
    'VARCHAR(64) NULL COMMENT ''离线记录唯一标识(UUID)''');
CALL P_AddColumnIfNotExists(@db, 'bi_building', 'new_root_object_id',
    'BIGINT NULL COMMENT ''新规范object树根节点ID(桥跨评定单元,node_type=UNIT),旧规范建筑为NULL''');

-- ================================================================================
-- F. 版本隔离（规范版本只归属 project / task）
-- ================================================================================
CALL P_AddColumnIfNotExists(@db, 'bi_project', 'std_version',
    'VARCHAR(32) NULL COMMENT ''规范版本:5230-2026(新)/H21-2011(旧),NULL视同旧规范''');
CALL P_AddColumnIfNotExists(@db, 'bi_task', 'std_version',
    'VARCHAR(32) NULL COMMENT ''规范版本:5230-2026(新)/H21-2011(旧),NULL视同旧规范''');

-- ================================================================================
-- G. 延后建普通索引（列存在后补非唯一索引）
-- ================================================================================
SET @idx_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bi_building' AND INDEX_NAME = 'idx_new_root_object_id'
);
SET @ddl_idx = IF(@idx_exists = 0,
    'ALTER TABLE `bi_building` ADD KEY `idx_new_root_object_id` (`new_root_object_id`)',
    'SELECT 1');
PREPARE stmt_idx FROM @ddl_idx; EXECUTE stmt_idx; DEALLOCATE PREPARE stmt_idx;

-- 清理工具过程
DROP PROCEDURE IF EXISTS P_AddColumnIfNotExists;

-- ================================================================================
-- 校验（跑完取消注释查看，确认关键列已落地）
-- ================================================================================
-- SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
--   WHERE TABLE_SCHEMA = DATABASE()
--     AND ((TABLE_NAME='bi_template_object'
--          AND COLUMN_NAME IN ('weight','impact_factor','category_i','props','gamma','omega'))
--       OR (TABLE_NAME='bi_building' AND COLUMN_NAME IN ('offline_uuid','new_root_object_id'))
--       OR (TABLE_NAME IN ('bi_project','bi_task') AND COLUMN_NAME='std_version')
--       OR (TABLE_NAME='bi_object' AND COLUMN_NAME IN ('node_type','gamma','omega','props_json')));
