-- ================================================================================
-- P0 回滚脚本：JTG/T 5230—2026 桥跨评定 + 离线同步合并改造 逆向
-- 脚本名：20261004_p0_span_sync_rollback.sql
-- 目标库：bi (MySQL 8.0.42)
-- 说明：与 20261004_p0_span_sync_ddl.sql 一一对应。旧表旧列零破坏前提下，
--       按「新表 DROP → 新列 DROP → 新索引 DROP」逆序回退。
--       注意：DROP COLUMN 会丢弃该列数据，执行前务必确认已备份/不再需要。
-- ================================================================================

-- 幂等工具
DROP PROCEDURE IF EXISTS `P0_DropColumnIfExists`;
DROP PROCEDURE IF EXISTS `P0_DropIndexIfExists`;

DELIMITER $$

CREATE PROCEDURE `P0_DropColumnIfExists`(
    IN p_db VARCHAR(64), IN p_tbl VARCHAR(64), IN p_col VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = p_db AND TABLE_NAME = p_tbl AND COLUMN_NAME = p_col
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_tbl, '` DROP COLUMN `', p_col, '`');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
    END IF;
END $$

CREATE PROCEDURE `P0_DropIndexIfExists`(
    IN p_db VARCHAR(64), IN p_tbl VARCHAR(64), IN p_idx VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = p_db AND TABLE_NAME = p_tbl AND INDEX_NAME = p_idx
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_tbl, '` DROP INDEX `', p_idx, '`');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
    END IF;
END $$

DELIMITER ;

SET @db = DATABASE();

-- ----------------------------------------------------------------------------
-- 一、删除全新业务表（数据会一并删除，需确认）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `bi_span_component_part`;
DROP TABLE IF EXISTS `bi_span_evaluation`;
DROP TABLE IF EXISTS `bi_user_sqlite`;
-- 注意：bi_id_mapping / bi_sync_log 属既有 20260409 脚本，本回滚不删除（不归 P0 管）

-- ----------------------------------------------------------------------------
-- 二、删除唯一索引
-- ----------------------------------------------------------------------------
-- 注意：P0 不再建唯一索引（uk_*_offline_uuid 已延后到 P2 同步线上线前执行），
--      故 P0 回滚无需删除它们。若 P2 已执行建唯一索引后再回滚，需另写 P2 回滚脚本删除：
-- CALL P0_DropIndexIfExists(@db, 'bi_object',         'uk_object_offline_uuid');
-- CALL P0_DropIndexIfExists(@db, 'bi_component',      'uk_component_offline_uuid');
-- CALL P0_DropIndexIfExists(@db, 'bi_disease',        'uk_disease_offline_uuid');
-- CALL P0_DropIndexIfExists(@db, 'bi_disease_detail', 'uk_detail_offline_uuid');
-- CALL P0_DropIndexIfExists(@db, 'bi_attachment',     'uk_attachment_offline_uuid');

-- ----------------------------------------------------------------------------
-- 三、删除普通索引
-- ----------------------------------------------------------------------------
-- 注意：P0 不再建这 2 个普通索引（idx_object_node_type/idx_object_span_no 已延后到 P1），
--      故 P0 回滚无需删除它们。若 P1 已执行建索引后再回滚，需另写 P1 回滚脚本删除：
-- CALL P0_DropIndexIfExists(@db, 'bi_object', 'idx_object_node_type');
-- CALL P0_DropIndexIfExists(@db, 'bi_object', 'idx_object_span_no');

-- ----------------------------------------------------------------------------
-- 四、删除新标语义列（bi_object 6 列）
-- ----------------------------------------------------------------------------
CALL P0_DropColumnIfExists(@db, 'bi_object', 'node_type');
CALL P0_DropColumnIfExists(@db, 'bi_object', 'span_no');
CALL P0_DropColumnIfExists(@db, 'bi_object', 'bridge_type');
CALL P0_DropColumnIfExists(@db, 'bi_object', 'gamma');
CALL P0_DropColumnIfExists(@db, 'bi_object', 'omega');
CALL P0_DropColumnIfExists(@db, 'bi_object', 'props_json');

-- ----------------------------------------------------------------------------
-- 五、删除离线同步字段（仅 bi_object + 采集表；下发只读表无 uuid 列）
-- ----------------------------------------------------------------------------
-- bi_object
CALL P0_DropColumnIfExists(@db, 'bi_object', 'offline_uuid');
CALL P0_DropColumnIfExists(@db, 'bi_object', 'parent_uuid');
CALL P0_DropColumnIfExists(@db, 'bi_object', 'is_offline_data');
CALL P0_DropColumnIfExists(@db, 'bi_object', 'offline_deleted');

-- bi_component
CALL P0_DropColumnIfExists(@db, 'bi_component', 'offline_uuid');
CALL P0_DropColumnIfExists(@db, 'bi_component', 'object_uuid');
CALL P0_DropColumnIfExists(@db, 'bi_component', 'is_offline_data');
CALL P0_DropColumnIfExists(@db, 'bi_component', 'offline_deleted');

-- bi_disease
CALL P0_DropColumnIfExists(@db, 'bi_disease', 'offline_uuid');
CALL P0_DropColumnIfExists(@db, 'bi_disease', 'object_uuid');
CALL P0_DropColumnIfExists(@db, 'bi_disease', 'component_uuid');
CALL P0_DropColumnIfExists(@db, 'bi_disease', 'is_offline_data');
CALL P0_DropColumnIfExists(@db, 'bi_disease', 'offline_deleted');

-- bi_disease_detail
CALL P0_DropColumnIfExists(@db, 'bi_disease_detail', 'offline_uuid');
CALL P0_DropColumnIfExists(@db, 'bi_disease_detail', 'disease_uuid');
CALL P0_DropColumnIfExists(@db, 'bi_disease_detail', 'is_offline_data');
CALL P0_DropColumnIfExists(@db, 'bi_disease_detail', 'offline_deleted');

-- bi_attachment
CALL P0_DropColumnIfExists(@db, 'bi_attachment', 'offline_uuid');
CALL P0_DropColumnIfExists(@db, 'bi_attachment', 'offline_subject_uuid');
CALL P0_DropColumnIfExists(@db, 'bi_attachment', 'is_offline_data');
CALL P0_DropColumnIfExists(@db, 'bi_attachment', 'offline_deleted');

-- ----------------------------------------------------------------------------
-- 六、清理存储过程
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `P0_DropColumnIfExists`;
DROP PROCEDURE IF EXISTS `P0_DropIndexIfExists`;

-- ============================== P0 回滚脚本结束 ==============================
