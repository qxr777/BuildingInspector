-- ================================================================================
-- 为 bi_template_object 新增 γ/ω 字段（对齐 bi_object.gamma/omega）
-- 脚本名：20261004_p0_template_object_add_gamma_omega.sql
-- 目标库：bi (MySQL 8.0.42, 60.205.13.156:3306)
--
-- 背景：JTG/T 5230—2026 附录 B 表 B.1.1 的 21 类桥型要以完整树记录在
--       bi_template_object 中（根=桥型 / 中层=结构层 / 叶=部件）。
--       结构层 γ 与部件 ω 需与 bi_object 表字段名保持一致（gamma/omega）。
--
-- 决策（2026-10-04 用户拍板）：
--   1. 保留 weight（旧模板标准权重，bi_object.standard_weight 的 join 来源，不能动）；
--   2. 保留 impact_factor（旧「影响系数Ƴ」，历史数据兼容，暂不迁移）；
--   3. 新增 gamma（结构层影响系数γ，对齐 bi_object.gamma 语义）与
--      omega（部件权重ω，0~100 整数，对齐 bi_object.omega 语义）；
--   4. 21 棵新树的 γ/ω 数据写入新增的 gamma/omega 列，与旧 weight/impact_factor 隔离。
--
-- 类型对齐 bi_object：
--   bi_object.gamma = DECIMAL(6,4)
--   bi_object.omega = INT
-- ================================================================================

SET NAMES utf8mb4;

-- 幂等：仅当列不存在时才 ADD（MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用存储过程探测）
DELIMITER $$
DROP PROCEDURE IF EXISTS P0_AddTemplateGammaOmega$$
CREATE PROCEDURE P0_AddTemplateGammaOmega()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'bi_template_object'
          AND COLUMN_NAME = 'gamma'
    ) THEN
        ALTER TABLE `bi_template_object`
            ADD COLUMN `gamma` DECIMAL(6,4) NULL COMMENT '结构层影响系数γ(附录B按桥型,与bi_object.gamma对齐)' AFTER `impact_factor`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'bi_template_object'
          AND COLUMN_NAME = 'omega'
    ) THEN
        ALTER TABLE `bi_template_object`
            ADD COLUMN `omega` INT NULL COMMENT '部件权重ω(附录B,0~100整数,与bi_object.omega对齐)' AFTER `gamma`;
    END IF;
END$$
DELIMITER ;

CALL P0_AddTemplateGammaOmega();
DROP PROCEDURE IF EXISTS P0_AddTemplateGammaOmega;

-- ================================================================================
-- 校验：应能看到 gamma、omega 两列（且 weight、impact_factor 仍在）
-- ================================================================================
-- SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
-- FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = 'bi' AND TABLE_NAME = 'bi_template_object'
-- ORDER BY ORDINAL_POSITION;
