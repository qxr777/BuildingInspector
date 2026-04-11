-- ------------------------------------------------------------------------------------------------
-- 离线数据同步数据库全量变更脚本 (Production-Ready / 幂等无侵入版)
-- 目标: MySQL (后端) 
-- 特性: 
-- 1. 采用高阶存储过程实现 "IF NOT EXISTS" 的幂等字段扩充，无论重复执行多少次都不会报错
-- 2. 全量覆盖 6 张核心关联表及其层级维度的 UUID (含 Parent UUIDs)
-- 3. 补齐所有服务端用于同步反解析的缓存表
-- ------------------------------------------------------------------------------------------------

-- =========================================================================
-- 第一部分：定义高可用幂等追加字段与索引存储过程
-- =========================================================================
DROP PROCEDURE IF EXISTS `AddColumnIfNotExists`;
DROP PROCEDURE IF EXISTS `AddUniqueIndexIfNotExists`;

DELIMITER $$

-- 1. 字段添加方法
CREATE PROCEDURE `AddColumnIfNotExists` (
    IN dbName VARCHAR(64),
    IN tableName VARCHAR(64),
    IN columnName VARCHAR(64),
    IN columnDef TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = dbName 
          AND TABLE_NAME = tableName 
          AND COLUMN_NAME = columnName
    ) THEN
        SET @sqlStatement = CONCAT('ALTER TABLE `', tableName, '` ADD COLUMN `', columnName, '` ', columnDef);
        PREPARE dynamic_statement FROM @sqlStatement;
        EXECUTE dynamic_statement;
        DEALLOCATE PREPARE dynamic_statement;
    END IF;
END $$

-- 2. 唯一索引添加方法
CREATE PROCEDURE `AddUniqueIndexIfNotExists` (
    IN dbName VARCHAR(64),
    IN tableName VARCHAR(64),
    IN indexName VARCHAR(64),
    IN indexColumns VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = dbName 
          AND TABLE_NAME = tableName 
          AND INDEX_NAME = indexName
    ) THEN
        SET @sqlStatement = CONCAT('ALTER TABLE `', tableName, '` ADD UNIQUE KEY `', indexName, '` (', indexColumns, ')');
        PREPARE dynamic_statement FROM @sqlStatement;
        EXECUTE dynamic_statement;
        DEALLOCATE PREPARE dynamic_statement;
    END IF;
END $$

DELIMITER ;

-- =========================================================================
-- 第二部分：批量执行核心业务表字段补全与索引挂载
-- 说明: 动态获取当前使用的 Database (@currentDB)，防止多环境库名不同的硬编码
-- =========================================================================
SET @currentDB = DATABASE();

-- 【1】桥梁基本信息表 (bi_building)
CALL AddColumnIfNotExists(@currentDB, 'bi_building', 'offline_uuid', 'varchar(64) DEFAULT NULL COMMENT \'离线记录唯一标识(UUID)\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_building', 'root_object_uuid', 'varchar(64) DEFAULT NULL COMMENT \'根对象的离线UUID\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_building', 'is_offline_data', 'tinyint(1) DEFAULT 0 COMMENT \'是否为离线数据(0:否, 1:是)\'');
CALL AddUniqueIndexIfNotExists(@currentDB, 'bi_building', 'idx_building_uuid', 'offline_uuid');

-- 【2】结构物/部件树表 (bi_object)
CALL AddColumnIfNotExists(@currentDB, 'bi_object', 'offline_uuid', 'varchar(64) DEFAULT NULL COMMENT \'离线记录唯一标识(UUID)\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_object', 'parent_uuid', 'varchar(64) DEFAULT NULL COMMENT \'父级结点的离线UUID\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_object', 'building_uuid', 'varchar(64) DEFAULT NULL COMMENT \'关联桥梁的离线UUID\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_object', 'is_offline_data', 'tinyint(1) DEFAULT 0 COMMENT \'是否为离线同步数据(0:否, 1:是)\'');
CALL AddUniqueIndexIfNotExists(@currentDB, 'bi_object', 'idx_object_uuid', 'offline_uuid');

-- 【3】具体部件表 (bi_component)
CALL AddColumnIfNotExists(@currentDB, 'bi_component', 'offline_uuid', 'varchar(64) DEFAULT NULL COMMENT \'离线记录唯一标识(UUID)\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_component', 'object_uuid', 'varchar(64) DEFAULT NULL COMMENT \'关联结构物的离线UUID\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_component', 'is_offline_data', 'tinyint(1) DEFAULT 0 COMMENT \'是否为离线同步数据(0:否, 1:是)\'');
CALL AddUniqueIndexIfNotExists(@currentDB, 'bi_component', 'idx_component_uuid', 'offline_uuid');

-- 【4】病害记录主表 (bi_disease)
CALL AddColumnIfNotExists(@currentDB, 'bi_disease', 'offline_uuid', 'varchar(64) DEFAULT NULL COMMENT \'离线记录唯一标识(UUID)\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_disease', 'building_uuid', 'varchar(64) DEFAULT NULL COMMENT \'关联桥梁的离线UUID\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_disease', 'object_uuid', 'varchar(64) DEFAULT NULL COMMENT \'关联结构物的离线UUID\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_disease', 'component_uuid', 'varchar(64) DEFAULT NULL COMMENT \'关联部件的离线UUID\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_disease', 'is_offline_data', 'tinyint(1) DEFAULT 0 COMMENT \'是否为离线同步数据(0:否, 1:是)\'');
CALL AddUniqueIndexIfNotExists(@currentDB, 'bi_disease', 'idx_disease_uuid', 'offline_uuid');

-- 【5】病害详情表 (bi_disease_detail) 
CALL AddColumnIfNotExists(@currentDB, 'bi_disease_detail', 'offline_uuid', 'varchar(64) DEFAULT NULL COMMENT \'离线记录唯一标识(UUID)\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_disease_detail', 'disease_uuid', 'varchar(64) DEFAULT NULL COMMENT \'关联病害的离线UUID\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_disease_detail', 'is_offline_data', 'tinyint(1) DEFAULT 0 COMMENT \'是否为离线数据(0:否, 1:是)\'');
CALL AddUniqueIndexIfNotExists(@currentDB, 'bi_disease_detail', 'idx_detail_uuid', 'offline_uuid');

-- 【6】附件/多媒体表 (bi_attachment)
CALL AddColumnIfNotExists(@currentDB, 'bi_attachment', 'offline_uuid', 'varchar(64) DEFAULT NULL COMMENT \'离线多媒体记录外标(UUID)\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_attachment', 'offline_subject_uuid', 'varchar(64) DEFAULT NULL COMMENT \'被挂载主体的离线UUID(对象可能为病害等)\'');
CALL AddColumnIfNotExists(@currentDB, 'bi_attachment', 'is_offline_data', 'tinyint(1) DEFAULT 0 COMMENT \'是否为离线同步数据(0:否, 1:是)\'');
CALL AddUniqueIndexIfNotExists(@currentDB, 'bi_attachment', 'idx_attachment_uuid', 'offline_uuid');

-- 善后：清理一次性高定存储过程释放资源
DROP PROCEDURE IF EXISTS `AddColumnIfNotExists`;
DROP PROCEDURE IF EXISTS `AddUniqueIndexIfNotExists`;

-- =========================================================================
-- 第三部分：业务后端配套支撑与映射表 (全新创建)
-- =========================================================================

-- 7. 离线 UUID 与服务端真实落库 ID 映射集 (Topology Resolving 关键)
CREATE TABLE IF NOT EXISTS `bi_id_mapping` (
  `table_name` varchar(64) NOT NULL COMMENT '数据流经实体映射的基础表名',
  `offline_uuid` varchar(64) NOT NULL COMMENT '移动端或中间层分配的离线主键UUID',
  `server_id` bigint(20) NOT NULL COMMENT '服务端入库后的真实物理主键ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '该映射关系的绑定时间',
  PRIMARY KEY (`table_name`, `offline_uuid`),
  KEY `idx_server_id` (`server_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离线数据上行树状拓扑重解 ID 映射图';

-- 8. 离线同步审计日志表 (全量跟踪)
CREATE TABLE IF NOT EXISTS `bi_sync_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '审计账本ID',
  `sync_uuid` varchar(64) DEFAULT NULL COMMENT '同步批次UUID',
  `user_id` bigint(20) DEFAULT NULL COMMENT '操作人ID',
  `status` int(11) DEFAULT '0' COMMENT '状态: 0=进行中, 1=成功, 2=失败',
  `client_info` varchar(255) DEFAULT NULL COMMENT '客户端信息(App版本,设备号等)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '同步日志记录时间',
  `finish_time` datetime DEFAULT NULL COMMENT '同步完成时间',
  `remark` text COMMENT '同步详细结果/错误备注',
  PRIMARY KEY (`id`),
  KEY `idx_sync_uuid` (`sync_uuid`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离线数据同步审计日志表';
