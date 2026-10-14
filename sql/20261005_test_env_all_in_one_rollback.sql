-- ================================================================================
-- 测试环境 MySQL 全量变更回滚脚本（与 20261005_test_env_all_in_one.sql 对应，幂等）
-- 脚本名：20261005_test_env_all_in_one_rollback.sql
-- 目标库：bi (MySQL 8.0.42)
-- 生成依据：2026-10-06 用户要求为 all_in_one.sql 配 rollback
--
-- 回滚范围（严格对应 all_in_one.sql 的净效果）：
--   [1] 删除 5 张全量新建表：bi_id_mapping / bi_sync_log /
--       bi_span_component_part / bi_span_evaluation / bi_user_sqlite
--   [2] 删除 33 个 ADD COLUMN（覆盖 bi_object / bi_component / bi_disease /
--       bi_disease_detail / bi_attachment / bi_template_object(仅 gamma,omega) /
--       bi_building / bi_project / bi_task）
--   [3] 删除延后建的普通索引 idx_new_root_object_id（随列删除会自动级联，仍显式清理）
--   [4] 删除种子数据：21 桥型顶层(900001~900021) + B01 完整树(910001~910015)
--
-- ⚠️ 重要前提与风险：
--   本脚本按「all_in_one.sql 是测试环境从裸表一把构建」的语义设计，回滚会
--   移除其引入的全部【新标/离线同步】列。但 bi_template_object 的
--   weight / impact_factor / category_i / props 四列为历史脚本引入、且已在用的列
--   （weight 是原始 bi_schema 建表列，impact_factor/category_i/props 来自 20260417），
--   经用户确认 weight 等在用、不可删——本回滚【绝不删除】这四列，仅删除新规范
--   真正新增的 gamma / omega 两列。请务必确认运行环境仅为测试库，避免误伤。
--
-- 幂等性：DROP TABLE IF EXISTS + 探测列存在性的存储过程 + DELETE（无匹配行无害）。
-- 执行顺序与内容见下方分段。
-- ================================================================================

SET NAMES utf8mb4;

-- ================================================================================
-- 第一部分：幂等工具存储过程（列 存在性探测 + 删除）
-- ================================================================================
DROP PROCEDURE IF EXISTS `P_DropColumnIfExists`;

DELIMITER $$

CREATE PROCEDURE `P_DropColumnIfExists`(
    IN p_db  VARCHAR(64),
    IN p_tbl VARCHAR(64),
    IN p_col VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = p_db AND TABLE_NAME = p_tbl AND COLUMN_NAME = p_col
    ) THEN
        -- 不指定 ALGORITHM，让 MySQL 自行选择；列删除通常需要 INPLACE/COPY，
        -- 回滚场景对耗时不敏感，正确性优先。
        SET @ddl = CONCAT('ALTER TABLE `', p_tbl, '` DROP COLUMN `', p_col, '`');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

SET @db = DATABASE();

-- ================================================================================
-- 第二部分：删除全量新建表（5 张）
--   这些表完全由 all_in_one 第二部分 / 第五部分创建，无历史来源，可整体删除。
-- ================================================================================
DROP TABLE IF EXISTS `bi_id_mapping`;
DROP TABLE IF EXISTS `bi_sync_log`;
DROP TABLE IF EXISTS `bi_span_component_part`;
DROP TABLE IF EXISTS `bi_span_evaluation`;
DROP TABLE IF EXISTS `bi_user_sqlite`;

-- ================================================================================
-- 第三部分：删除延后建的普通索引（随 new_root_object_id 列删除会级联，仍显式清理）
-- ================================================================================
SET @idx_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bi_building' AND INDEX_NAME = 'idx_new_root_object_id'
);
SET @ddl_idx = IF(@idx_exists = 0,
    'SELECT 1',
    'ALTER TABLE `bi_building` DROP KEY `idx_new_root_object_id`'
);
PREPARE stmt_idx FROM @ddl_idx; EXECUTE stmt_idx; DEALLOCATE PREPARE stmt_idx;

-- ================================================================================
-- 第四部分：删除 37 个 ADD COLUMN
-- ================================================================================

-- 【1】bi_object（10 列：新标语义 6 + 离线同步 4）
CALL P_DropColumnIfExists(@db, 'bi_object', 'node_type');
CALL P_DropColumnIfExists(@db, 'bi_object', 'span_no');
CALL P_DropColumnIfExists(@db, 'bi_object', 'bridge_type');
CALL P_DropColumnIfExists(@db, 'bi_object', 'gamma');
CALL P_DropColumnIfExists(@db, 'bi_object', 'omega');
CALL P_DropColumnIfExists(@db, 'bi_object', 'props_json');
CALL P_DropColumnIfExists(@db, 'bi_object', 'offline_uuid');
CALL P_DropColumnIfExists(@db, 'bi_object', 'parent_uuid');
CALL P_DropColumnIfExists(@db, 'bi_object', 'is_offline_data');
CALL P_DropColumnIfExists(@db, 'bi_object', 'offline_deleted');

-- 【2】bi_component（4 列：离线同步）
CALL P_DropColumnIfExists(@db, 'bi_component', 'offline_uuid');
CALL P_DropColumnIfExists(@db, 'bi_component', 'object_uuid');
CALL P_DropColumnIfExists(@db, 'bi_component', 'is_offline_data');
CALL P_DropColumnIfExists(@db, 'bi_component', 'offline_deleted');

-- 【3】bi_disease（5 列：离线同步）
CALL P_DropColumnIfExists(@db, 'bi_disease', 'offline_uuid');
CALL P_DropColumnIfExists(@db, 'bi_disease', 'object_uuid');
CALL P_DropColumnIfExists(@db, 'bi_disease', 'component_uuid');
CALL P_DropColumnIfExists(@db, 'bi_disease', 'is_offline_data');
CALL P_DropColumnIfExists(@db, 'bi_disease', 'offline_deleted');

-- 【4】bi_disease_detail（4 列：离线同步）
CALL P_DropColumnIfExists(@db, 'bi_disease_detail', 'offline_uuid');
CALL P_DropColumnIfExists(@db, 'bi_disease_detail', 'disease_uuid');
CALL P_DropColumnIfExists(@db, 'bi_disease_detail', 'is_offline_data');
CALL P_DropColumnIfExists(@db, 'bi_disease_detail', 'offline_deleted');

-- 【5】bi_attachment（4 列：离线同步）
CALL P_DropColumnIfExists(@db, 'bi_attachment', 'offline_uuid');
CALL P_DropColumnIfExists(@db, 'bi_attachment', 'offline_subject_uuid');
CALL P_DropColumnIfExists(@db, 'bi_attachment', 'is_offline_data');
CALL P_DropColumnIfExists(@db, 'bi_attachment', 'offline_deleted');

-- 【6】bi_template_object（仅删新标引入的 gamma / omega；见头部风险说明）
--     weight / impact_factor / category_i / props 为历史脚本(原始 schema / 20260417)
--     引入、已在生产/准生产使用的列，本回滚【不删】，避免误删在用数据。
CALL P_DropColumnIfExists(@db, 'bi_template_object', 'gamma');
CALL P_DropColumnIfExists(@db, 'bi_template_object', 'omega');

-- 【7】bi_building（2 列：offline_uuid + new_root_object_id）
CALL P_DropColumnIfExists(@db, 'bi_building', 'offline_uuid');
CALL P_DropColumnIfExists(@db, 'bi_building', 'new_root_object_id');

-- 【8】bi_project / bi_task（各 1 列：std_version）
CALL P_DropColumnIfExists(@db, 'bi_project', 'std_version');
CALL P_DropColumnIfExists(@db, 'bi_task', 'std_version');

-- ================================================================================
-- 第五部分：删除种子数据
--   21 桥型顶层(900001~900021) + B01 完整树(910001~910015)
--   DELETE 无匹配行无害，可重复执行。
-- ================================================================================
DELETE FROM `bi_template_object`
WHERE `id` BETWEEN 900001 AND 900021
   OR `id` BETWEEN 910001 AND 910015;

-- ================================================================================
-- 第六部分：清理存储过程
-- ================================================================================
DROP PROCEDURE IF EXISTS `P_DropColumnIfExists`;

-- ================================================================================
-- 校验（可选，取消注释执行）
-- ================================================================================
-- 1) 新建表应已全部消失
-- SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN
--    ('bi_id_mapping','bi_sync_log','bi_span_component_part','bi_span_evaluation','bi_user_sqlite');
-- 2) 关键列应已消失
-- SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND ((TABLE_NAME IN ('bi_project','bi_task') AND COLUMN_NAME='std_version')
--      OR (TABLE_NAME='bi_building' AND COLUMN_NAME IN ('offline_uuid','new_root_object_id'))
--      OR (TABLE_NAME='bi_object' AND COLUMN_NAME IN ('node_type','span_no','bridge_type','gamma','omega','props_json','offline_uuid','parent_uuid','is_offline_data','offline_deleted'))
--      OR (TABLE_NAME='bi_template_object' AND COLUMN_NAME IN ('gamma','omega')));
-- 3) 种子应已消失（应返回 0）
-- SELECT COUNT(*) FROM bi_template_object WHERE id BETWEEN 900001 AND 900021 OR id BETWEEN 910001 AND 910015;

-- ============================== 回滚脚本结束 ==============================
