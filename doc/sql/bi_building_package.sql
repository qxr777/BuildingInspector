CREATE TABLE `bi_building_package` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `building_id` bigint(20) DEFAULT NULL COMMENT '关联建筑物ID',
  `minio_id` bigint(20) DEFAULT NULL COMMENT '关联的MinIO文件映射ID',
  `package_time` datetime DEFAULT NULL COMMENT '打包时间',
  `package_size` varchar(50) DEFAULT NULL COMMENT '包大小',
  PRIMARY KEY (`id`),
  KEY `idx_building_id` (`building_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='建筑物病害附件打包记录表';
