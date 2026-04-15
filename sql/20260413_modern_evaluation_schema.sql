-- =================================================================================
-- 2026 公路桥梁技术状况评定标准 - 数据库结构初始化脚本
-- 合并自: 20260412_eval_comp_detail_ddl.sql, 20260412_span_evaluation_support.sql
--       20260413_disease_evaluation_support.sql, 20260413_edi_merge.sql
-- =================================================================================

-- 1. 扩展 bi_object 支持桥跨元数据
ALTER TABLE bi_object ADD COLUMN span_index INT COMMENT '跨号 (从1开始)';
ALTER TABLE bi_object ADD COLUMN span_length DECIMAL(10,2) COMMENT '跨径 (单位:米, 用于全桥得分加权)';

-- 2. 增强 bi_evaluation 支持多维评定与指标扩展
ALTER TABLE bi_evaluation ADD COLUMN target_type VARCHAR(20) DEFAULT 'BRIDGE' COMMENT '评定目标类型: BRIDGE(全桥), SPAN(分跨)';
ALTER TABLE bi_evaluation ADD COLUMN target_id BIGINT COMMENT '评定目标对象ID (bi_object_id)';
ALTER TABLE bi_evaluation ADD COLUMN acci_score DECIMAL(10,2) COMMENT '附属构造技术状况评定得分 (ACCI)';
ALTER TABLE bi_evaluation ADD COLUMN acci_level INT COMMENT '附属构造技术状况等级';

-- 初始化现有全桥评定数据的 target_type
UPDATE bi_evaluation SET target_type = 'BRIDGE' WHERE target_type IS NULL;

-- 3. 新建分跨构件关联表 (支持共享构件权重拓扑) - 2026标多对多关联方案
CREATE TABLE IF NOT EXISTS `bi_object_component` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `component_id` BIGINT DEFAULT NULL COMMENT '构件ID (关联bi_component.id)',
    `bi_object_id` BIGINT DEFAULT NULL COMMENT '对象ID (关联bi_object.id，主要用于桥跨)',
    `component_uuid` VARCHAR(64) COMMENT '构件离线UUID',
    `object_uuid` VARCHAR(64) COMMENT '对象离线UUID',
    `weight` DECIMAL(10,4) DEFAULT 1.0000 COMMENT '该构件在特定分单元评定中的贡献权重',
    `offline_uuid` VARCHAR(64) COMMENT '离线唯一标识符',
    `is_offline_data` TINYINT(1) DEFAULT 0 COMMENT '是否为离线生成数据',
    `create_by` VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_component (component_id),
    KEY idx_bi_object (bi_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='2026新标-构件与对象多对多关联表';

-- 4. 新建构件评定指标事实表 (用于存储单次评定任务中的标度快照)
CREATE TABLE IF NOT EXISTS `bi_evaluation_component_detail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint(20) NOT NULL COMMENT '评定任务ID',
  `span_id` bigint(20) NOT NULL COMMENT '关联跨径ID (对应bi_object_id)',
  `component_id` bigint(20) NOT NULL COMMENT '构件ID (对应bi_component的id)',
  `edi` int(2) DEFAULT NULL COMMENT '构件缺损状况标度',
  `efi` int(2) DEFAULT NULL COMMENT '构件功能状况标度 (0~2)',
  `eai` int(2) DEFAULT NULL COMMENT '构件影响状况标度 (-1~1)',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_span` (`task_id`, `span_id`),
  KEY `idx_task_component` (`task_id`, `component_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='2026标构件评定指标事实表';

-- 5. 构件 (bi_component) 评定字段合并与扩展
-- 增加 EDI (取代过时的 ESDI/EDDI), EFI, EAI
ALTER TABLE bi_component 
ADD COLUMN `edi` int(2) DEFAULT NULL COMMENT '构件缺损状况标度',
ADD COLUMN `efi` int(2) DEFAULT NULL COMMENT '构件功能状况 (0~2)',
ADD COLUMN `eai` int(2) DEFAULT NULL COMMENT '构件影响状况 (-1~1)';

-- 如果存在旧字段则进行合并迁移 (选看)
-- UPDATE bi_component SET edi = GREATEST(COALESCE(esdi, 0), COALESCE(eddi, 0));


-- 6. 病害 (bi_disease) 评定字段扩展
ALTER TABLE bi_disease
ADD COLUMN `edi` INT NULL COMMENT '病害缺损程度标度',
ADD COLUMN `efi` INT NULL COMMENT '功能影响标度',
ADD COLUMN `eai` INT NULL COMMENT '发展趋势标度';
