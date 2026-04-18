-- 为 bi_template_object 新增影响系数Ƴ与类别（i）字段
SET NAMES utf8mb4;
ALTER TABLE `bi_template_object`
    ADD COLUMN `impact_factor` decimal(10,4) NULL DEFAULT NULL COMMENT '影响系数Ƴ' AFTER `weight`,
    ADD COLUMN `category_i` varchar(50) NULL DEFAULT NULL COMMENT '类别（i）' AFTER `impact_factor`;
