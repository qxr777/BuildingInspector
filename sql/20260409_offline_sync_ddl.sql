-- ------------------------------------------------------------------------------------------------
-- 离线数据同步数据库变更脚本 (Open-Closed Principle: 只增不改)
-- 目标: MySQL (后端) 
-- 说明: 补齐缺失的标示字段，用于离线同步状态追踪
-- ------------------------------------------------------------------------------------------------

-- 0. 补齐缺失的病害详情表 (如果之前执行失败，此处重试)
CREATE TABLE IF NOT EXISTS `bi_disease_detail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `disease_id` bigint(20) DEFAULT NULL COMMENT '关联病害ID',
  `reference1_location` varchar(255) DEFAULT NULL COMMENT '参考面1位置',
  `reference1_location_start` decimal(10,3) DEFAULT NULL COMMENT '参考面1起始位置',
  `reference1_location_end` decimal(10,3) DEFAULT NULL COMMENT '参考面1结束位置',
  `reference2_location` varchar(255) DEFAULT NULL COMMENT '参考面2位置',
  `reference2_location_start` decimal(10,3) DEFAULT NULL COMMENT '参考面2起始位置',
  `reference2_location_end` decimal(10,3) DEFAULT NULL COMMENT '参考面2结束位置',
  `length1` decimal(10,3) DEFAULT NULL COMMENT '长度1',
  `length2` decimal(10,3) DEFAULT NULL COMMENT '长度2',
  `length3` decimal(10,3) DEFAULT NULL COMMENT '长度3',
  `width` decimal(10,3) DEFAULT NULL COMMENT '宽度',
  `height_depth` decimal(10,3) DEFAULT NULL COMMENT '高度/深度',
  `crack_width` decimal(10,3) DEFAULT NULL COMMENT '缝宽',
  `area_length` decimal(10,3) DEFAULT NULL COMMENT '面积_长',
  `area_width` decimal(10,3) DEFAULT NULL COMMENT '面积_宽',
  `area_identifier` int(11) DEFAULT NULL COMMENT '面积标识符(0:普通, 1:平均, 2:总计)',
  `deformation` decimal(10,3) DEFAULT NULL COMMENT '变形/位移',
  `angle` int(11) DEFAULT NULL COMMENT '角度',
  `numerator_ratio` int(11) DEFAULT NULL COMMENT '比例-分子',
  `denominator_ratio` int(11) DEFAULT NULL COMMENT '比例-分母',
  `length_range_start` decimal(10,3) DEFAULT NULL COMMENT '长度范围起始',
  `length_range_end` decimal(10,3) DEFAULT NULL COMMENT '长度范围结束',
  `width_range_start` decimal(10,3) DEFAULT NULL COMMENT '宽度范围起始',
  `width_range_end` decimal(10,3) DEFAULT NULL COMMENT '宽度范围结束',
  `height_depth_range_start` decimal(10,3) DEFAULT NULL COMMENT '高度/深度范围起始',
  `height_depth_range_end` decimal(10,3) DEFAULT NULL COMMENT '高度/深度范围结束',
  `crack_width_range_start` decimal(10,3) DEFAULT NULL COMMENT '缝宽范围起始',
  `crack_width_range_end` decimal(10,3) DEFAULT NULL COMMENT '缝宽范围结束',
  `area_range_start` decimal(10,3) DEFAULT NULL COMMENT '面积范围起始',
  `area_range_end` decimal(10,3) DEFAULT NULL COMMENT '面积范围结束',
  `deformation_range_start` decimal(10,3) DEFAULT NULL COMMENT '变形范围起始',
  `deformation_range_end` decimal(10,3) DEFAULT NULL COMMENT '变形范围结束',
  `angle_range_start` decimal(10,3) DEFAULT NULL COMMENT '角度范围起始',
  `angle_range_end` decimal(10,3) DEFAULT NULL COMMENT '角度范围结束',
  `other` varchar(500) DEFAULT NULL COMMENT '其他',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `offline_uuid` varchar(64) DEFAULT NULL COMMENT '离线记录唯一标识(UUID)',
  `is_offline_data` tinyint(1) DEFAULT '0' COMMENT '是否为离线同步数据(0:否, 1:是)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_detail_uuid` (`offline_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病害指标详情表';

-- 1. 扩充现有的业务表结构 (仅添加缺失列)

-- 1.1 桥梁基本信息表 (offline_uuid 已存在)
ALTER TABLE `bi_building` 
ADD COLUMN `is_offline_data` tinyint(1) DEFAULT '0' COMMENT '是否为离线同步数据(0:否, 1:是)';

-- 1.2 结构物/部件树表 (offline_uuid 已存在)
ALTER TABLE `bi_object` 
ADD COLUMN `is_offline_data` tinyint(1) DEFAULT '0' COMMENT '是否为离线同步数据(0:否, 1:是)';

-- 1.3 具体部件表 (offline_uuid 已存在)
ALTER TABLE `bi_component` 
ADD COLUMN `is_offline_data` tinyint(1) DEFAULT '0' COMMENT '是否为离线同步数据(0:否, 1:是)';

-- 1.4 病害记录表 (offline_uuid 已存在)
ALTER TABLE `bi_disease` 
ADD COLUMN `is_offline_data` tinyint(1) DEFAULT '0' COMMENT '是否为离线同步数据(0:否, 1:是)';

-- 1.5 附件/照片表 (全缺)
ALTER TABLE `bi_attachment` 
ADD COLUMN `offline_uuid` varchar(64) DEFAULT NULL COMMENT '离线记录唯一标识(UUID)' AFTER `subject_id`,
ADD COLUMN `offline_subject_uuid` varchar(64) DEFAULT NULL COMMENT '关联主体的离线UUID(病害/桥梁)' AFTER `offline_uuid`,
ADD COLUMN `is_offline_data` tinyint(1) DEFAULT '0' COMMENT '是否为离线同步数据(0:否, 1:是)' AFTER `offline_subject_uuid`,
ADD UNIQUE KEY `idx_attachment_uuid` (`offline_uuid`);
