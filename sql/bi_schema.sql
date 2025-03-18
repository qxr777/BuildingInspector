-- BI对象表
CREATE TABLE `bi_object` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '对象ID',
                             `name` varchar(20) NOT NULL COMMENT '对象名称',
                             `parent_id` bigint DEFAULT NULL COMMENT '父对象ID',
                             `ancestors` varchar(500) DEFAULT '' COMMENT '祖级列表',
                             `order_num` int DEFAULT 0 COMMENT '显示顺序',
                             `status` char(1) DEFAULT '0' COMMENT '对象状态（0正常 1停用）',
                             `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
                             `longitude` decimal(10,6) DEFAULT NULL COMMENT '经度',
                             `latitude` decimal(10,6) DEFAULT NULL COMMENT '纬度',
                             `altitude` decimal(10,2) DEFAULT NULL COMMENT '海拔高度',
                             `position` varchar(200) DEFAULT NULL COMMENT '位置',
                             `area` varchar(100) DEFAULT NULL COMMENT '区域',
                             `admin_dept` varchar(100) DEFAULT NULL COMMENT '管理部门',
                             `weight` decimal(10,2) DEFAULT NULL COMMENT '权重',
                             `video_feed` varchar(255) DEFAULT NULL COMMENT '视频流来源',
                             `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                             `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                             `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                             `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`),
                             KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI对象表';

-- 属性表
CREATE TABLE `bi_property` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '属性ID',
                               `name` varchar(100) NOT NULL COMMENT '属性名称',
                               `value` varchar(500) DEFAULT NULL COMMENT '属性值',
                               `parent_id` bigint DEFAULT NULL COMMENT '父属性ID',
                               `ancestors` varchar(500) DEFAULT '' COMMENT '祖级列表',
                               `order_num` int DEFAULT 0 COMMENT '显示顺序',
                               `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                               `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                               `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='属性表';

-- 建筑表
CREATE TABLE `bi_building` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '建筑ID',
                               `name` varchar(20) NOT NULL COMMENT '建筑名称',
                               `status` char(1) DEFAULT '0' COMMENT '建筑状态（0正常 1停用）',
                               `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
                               `longitude` decimal(10,6) DEFAULT NULL COMMENT '经度',
                               `latitude` decimal(10,6) DEFAULT NULL COMMENT '纬度',
                               `altitude` decimal(10,2) DEFAULT NULL COMMENT '海拔高度',
                               `address` varchar(200) DEFAULT NULL COMMENT '地址',
                               `area` varchar(100) DEFAULT NULL COMMENT '区域',
                               `admin_dept` varchar(100) DEFAULT NULL COMMENT '管理部门',
                               `weight` decimal(10,2) DEFAULT NULL COMMENT '权重',
                               `video_feed` varchar(255) DEFAULT NULL COMMENT '视频流来源',
                               `root_object_id` bigint DEFAULT NULL COMMENT '根对象ID',
                               `root_property_id` bigint DEFAULT NULL COMMENT '根属性ID',
                               `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                               `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                               `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                               `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               KEY `idx_root_object_id` (`root_object_id`),
                               KEY `idx_root_property_id` (`root_property_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='建筑表';

-- 病害表
CREATE TABLE `bi_disease` (
                              `id` bigint NOT NULL AUTO_INCREMENT COMMENT '病害ID',
                              `position` varchar(200) DEFAULT NULL COMMENT '病害位置',
                              `type` varchar(50) DEFAULT NULL COMMENT '病害类型',
                              `description` text COMMENT '病害描述',
                              `trend` varchar(50) DEFAULT NULL COMMENT '病害趋势',
                              `level` int DEFAULT NULL COMMENT '病害等级',
                              `quantity` int DEFAULT NULL COMMENT '病害数量',
                              `project_id` bigint DEFAULT NULL COMMENT '关联项目ID',
                              `bi_object_id` bigint DEFAULT NULL COMMENT '关联对象ID',
                              `building_id` bigint DEFAULT NULL COMMENT '关联建筑ID',
                              `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                              `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                              `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                              `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              KEY `idx_project_id` (`project_id`),
                              KEY `idx_bi_object_id` (`bi_object_id`),
                              KEY `idx_building_id` (`building_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病害表';

-- 设备表
CREATE TABLE `bi_device` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '设备ID',
                             `name` varchar(100) NOT NULL COMMENT '设备名称',
                             `model` varchar(100) DEFAULT NULL COMMENT '设备型号',
                             `purpose` varchar(200) DEFAULT NULL COMMENT '设备用途',
                             `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                             `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                             `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                             `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

-- 附件表
CREATE TABLE `bi_attachment` (
                                 `id` bigint NOT NULL AUTO_INCREMENT COMMENT '附件ID',
                                 `name` varchar(100) NOT NULL COMMENT '附件名称',
                                 `subject_id` bigint DEFAULT NULL COMMENT '关联主体ID',
                                 `type` int DEFAULT NULL COMMENT '附件类型（null为病害附件，2为设备附件）',
                                 `file_path` varchar(500) DEFAULT NULL COMMENT '文件路径',
                                 `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
                                 `file_type` varchar(50) DEFAULT NULL COMMENT '文件类型',
                                 `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                                 `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                                 `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 KEY `idx_subject_id` (`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件表';

-- 项目表
CREATE TABLE `bi_project` (
                              `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目ID',
                              `name` varchar(100) NOT NULL COMMENT '项目名称',
                              `year` int DEFAULT NULL COMMENT '项目年份',
                              `status` char(1) DEFAULT '0' COMMENT '项目状态（0正常 1停用）',
                              `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
                              `dept_id` bigint DEFAULT NULL COMMENT '项目受托部门ID',
                              `author_id` bigint DEFAULT NULL COMMENT '报告编写人员ID',
                              `reviewer_id` bigint DEFAULT NULL COMMENT '报告审核人员ID',
                              `approver_id` bigint DEFAULT NULL COMMENT '报告批准人员ID',
                              `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                              `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                              `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                              `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              KEY `idx_dept_id` (`dept_id`),
                              KEY `idx_author_id` (`author_id`),
                              KEY `idx_reviewer_id` (`reviewer_id`),
                              KEY `idx_approver_id` (`approver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

-- 项目建筑关联表
CREATE TABLE `bi_project_building` (
                                       `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                       `project_id` bigint NOT NULL COMMENT '项目ID',
                                       `building_id` bigint NOT NULL COMMENT '建筑ID',
                                       `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `uk_project_building` (`project_id`,`building_id`),
                                       KEY `idx_building_id` (`building_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目建筑关联表';

-- 标准表
CREATE TABLE `bi_standard` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标准ID',
                               `name` varchar(100) NOT NULL COMMENT '标准名称',
                               `standard_no` varchar(50) DEFAULT NULL COMMENT '标准编号',
                               `year` int DEFAULT NULL COMMENT '发布年份',
                               `publisher` varchar(100) DEFAULT NULL COMMENT '发布单位',
                               `attachment_id` bigint DEFAULT NULL COMMENT '关联附件ID',
                               `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                               `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                               `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                               `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               KEY `idx_attachment_id` (`attachment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标准表';

-- 任务表
CREATE TABLE `bi_task` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
                           `building_id` bigint DEFAULT NULL COMMENT '关联建筑ID',
                           `project_id` bigint DEFAULT NULL COMMENT '关联项目ID',
                           `inspector_id` bigint DEFAULT NULL COMMENT '检查人员ID',
                           `status` int DEFAULT 0 COMMENT '任务状态',
                           `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                           `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                           `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                           `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           KEY `idx_building_id` (`building_id`),
                           KEY `idx_project_id` (`project_id`),
                           KEY `idx_inspector_id` (`inspector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- 病害附件关联表
CREATE TABLE `bi_disease_attachment` (
                                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                         `disease_id` bigint NOT NULL COMMENT '病害ID',
                                         `attachment_id` bigint NOT NULL COMMENT '附件ID',
                                         `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                         PRIMARY KEY (`id`),
                                         KEY `idx_disease_id` (`disease_id`),
                                         KEY `idx_attachment_id` (`attachment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病害附件关联表';


