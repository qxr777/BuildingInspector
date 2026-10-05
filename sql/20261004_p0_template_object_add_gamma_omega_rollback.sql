-- ================================================================================
-- 回滚：删除 bi_template_object 新增的 gamma/omega 列
-- 脚本名：20261004_p0_template_object_add_gamma_omega_rollback.sql
-- 仅当确认 21 棵新树 γ/ω 尚未写入、或需整体回退时执行。
-- ================================================================================

SET NAMES utf8mb4;

ALTER TABLE `bi_template_object` DROP COLUMN `omega`;
ALTER TABLE `bi_template_object` DROP COLUMN `gamma`;
