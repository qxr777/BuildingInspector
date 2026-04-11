-- =============================================
-- 病害位置字典表 & 模板构件-病害位置关联表
-- @author QiXin
-- @date 2026-04-11
-- =============================================

-- 1. 病害位置字典表（从 bi_template_object 第五级抽离）
CREATE TABLE IF NOT EXISTS `bi_disease_position` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100) NOT NULL                COMMENT '位置名称（如：顶面、底面、侧面）',
    `code`        VARCHAR(50)  DEFAULT NULL             COMMENT '位置编码',
    `props`       VARCHAR(500) DEFAULT NULL             COMMENT '附加属性（JSON）',
    `ref1`        VARCHAR(255) DEFAULT NULL             COMMENT '参考面1',
    `ref2`        VARCHAR(255) DEFAULT NULL             COMMENT '参考面2',
    `sort_order`  INT          DEFAULT 0                COMMENT '排序号',
    `status`      CHAR(1)      DEFAULT '0'              COMMENT '状态（0正常 1停用）',
    `del_flag`    CHAR(1)      DEFAULT '0'              COMMENT '删除标志（0存在 2删除）',
    `remark`      VARCHAR(500) DEFAULT NULL             COMMENT '备注',
    `create_by`   VARCHAR(64)  DEFAULT NULL             COMMENT '创建者',
    `create_time` DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `update_by`   VARCHAR(64)  DEFAULT NULL             COMMENT '更新者',
    `update_time` DATETIME     DEFAULT NULL             COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病害位置字典表';

-- 2. 模板构件 ↔ 病害位置 关联表（多对多）
CREATE TABLE IF NOT EXISTS `bi_template_object_disease_position` (
    `template_object_id`  BIGINT NOT NULL COMMENT '模板构件ID（关联第4级 bi_template_object）',
    `disease_position_id` BIGINT NOT NULL COMMENT '病害位置ID（关联 bi_disease_position）',
    PRIMARY KEY (`template_object_id`, `disease_position_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板构件-病害位置关联表';
