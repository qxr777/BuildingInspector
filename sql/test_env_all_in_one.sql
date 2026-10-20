-- ================================================================================
-- 测试环境 MySQL 全量变更脚本（合并版，幂等，不含耗时回填）
-- 脚本名：20261005_test_env_all_in_one.sql
-- 目标库：bi (MySQL 8.0.42)
-- 合并来源：
--   ① 20260409_offline_sync_ddl.sql         —— 建 bi_id_mapping / bi_sync_log
--   ② 20261004_p0_span_sync_ddl.sql         —— 加列 + 建新表（新标语义列 + 离线同步列）
--   ③ 20261004_p0_template_object_add_gamma_omega.sql —— 模板表补 gamma/omega
--   ④ 20261005_p1_span_component_part_add_uuid_columns.sql —— 锚定表补 3 uuid
--   ⑤ 20261005_p1_building_add_offline_uuid.sql —— bi_building 补 offline_uuid
--   ⑥ 20261004_p0_template_5230_seed.sql    —— 【已移除】原 21 桥型固定 id 种子
--   ⑦ 20261005_test_b01_template_tree_seed.sql —— 【已移除】原 B01 固定 id 树
--   ⑧ 20261007_p2_std5230_dict_isolation.sql —— 版本隔离字典列 + 复合唯一索引 + 清理旧固定id种子（Step2）
--   ⑨ 20261013_p3_std5230_leaf_element_dict.sql —— bi_element 新构件表(与 bi_component 并存)、
--      叶子部件/病害位置/维修加固结构/材料耐久性 4 张字典 + 4 张绑定表、bi_object 加 leaf_object_id/is_main_component；
--      bi_element 加 material_durability_id；bi_disease_type 加 scale_type(EDI/EFI/EAI,缺省EDI)
--   ⑩ 20261007_v2_evaluation_engine.sql    —— 【已下线 2026-10-14】三级评定引擎后端整包移除，
--      bi_v2_component_input / bi_v2_absent_mark / bi_v2_single_control_mark /
--      bi_v2_component_evaluation / bi_v2_span_part_detail / bi_bridge_evaluation 六表 DROP
--      （Java 代码可由 git 11513368 恢复）；仅保留 bi_span_evaluation 覆盖留痕5列+uk（冻结）。
--      原独立脚本与其回滚脚本已删除，本文件为唯一收敛入口。
--   ※ 新模板数据不再由 SQL 写入，统一改由 Java 种子接口 POST /biz/stdSeed/run 落地；
--     另提供一次性种子快照 20261014_std5230_seed.sql（由 std YAML 生成，免调接口初始化环境）。
--
-- 合并决策（2026-10-05）：
--   1. 以 v1.6 决策 + 最终 P1 拍板为准，【去掉旧脚本中被 v1.6 否决的内容】：
--      - bi_building 不加 root_object_uuid、不加唯一索引 idx_building_uuid（只加 offline_uuid）；
--      - bi_object 不加 building_uuid；bi_disease 不加 building_uuid（整数 id 直通）。
--   2. 唯一索引【全部延后】到回填后（本脚本不含回填），避免存量 NULL 冲突。
--   3. 不含 P2 回填（用户明确排除耗时回填）。
--   4.（2026-10-13）bi_element 为【全新空表】，与旧 bi_component 并存：不 RENAME、不迁存量数据、
--      不动 bi_disease.component_id/component_uuid、bi_span_component_part 等旧链路；
--      bi_component 暂冻结保留，新评定链路使用 bi_element。
--
-- ⚡ 性能优化（针对「执行非常慢」，参考 P0 脚本，2026-10-05）：
--   1. 所有 ADD COLUMN 改为【可空 NULL、无显式 DEFAULT、无 AFTER 子句】，
--      且存储过程【不指定 ALGORITHM=INPLACE】，让 MySQL 8.0 自动选择 INSTANT 算法
--      （仅改元数据，秒级完成）。原先的 NOT NULL DEFAULT 0 / DEFAULT 'LEGACY' 会迫使
--      MySQL 走 INPLACE/COPY，需重写整张表（bi_object 391 万行），这是慢的根源。
--   2. 不在大表上建任何索引（唯一索引已延后到回填后，普通索引 node_type/span_no 也延后到 P1）。
--   3. 语义兜底：is_offline_data / offline_deleted / node_type 的默认值改由代码层保证
--      （App/后端插入时显式写 0 或 'LEGACY'），数据库不再用 DEFAULT 约束。
--
-- 执行顺序与内容：
--   [1] 建同步基础表 bi_id_mapping / bi_sync_log
--   [2] 5 张采集表加离线同步列 + bi_object 新标语义列
--   [3] 建 3 张新表 bi_span_element_part / bi_span_evaluation / bi_user_sqlite
--   [4] bi_template_object 补 gamma/omega
--   [5] bi_building 补 offline_uuid
--   [6] 种子数据：21 桥型顶层 + B01 完整树
--   [7] 版本隔离字典列 + 复合唯一索引（bi_disease_type / bi_disease_scale / bi_template_object）
--   [8] 5230 叶子部件/构件字典：bi_element + 3 字典表 + 5 绑定表（9 张新表；2026-10-16 增
--       bi_template_object_leaf_object 桥型模板PART-叶子多对多绑定；病害位置不建字典，
--       位置字符串直接存 bi_lo_disease_position.position；旧空表 bi_disease_position DROP），bi_object 加 2 列；
--       bi_element 加 material_durability_id 列；bi_disease_type 加 scale_type(EDI/EFI/EAI,缺省EDI)
--   [9] 5230 评定引擎（2026-10-14 整包下线）：bi_span_evaluation 覆盖留痕5列+uk_task_span 冻结保留；
--       DROP 引擎六表（bi_v2_* 五表 + bi_bridge_evaluation），使跑过旧版脚本的库收敛
--   [10] bi_user_sqlite 收敛为运行期结构（2026-10-15）：补 package_time/update_time/
--       package_size/del_flag 4 列，idx_user 升级为 uk_user_sqlite_user_id 唯一键
--
-- 幂等性：所有 ADD COLUMN / CREATE TABLE 均用存储过程探测存在性；
--         种子数据用 WHERE NOT EXISTS 按 id 判重。可重复执行。
-- ================================================================================

SET NAMES utf8mb4;

-- ================================================================================
-- 第一部分：幂等工具存储过程（字段 判断存在性）
-- ================================================================================
DROP PROCEDURE IF EXISTS `P_AddColumnIfNotExists`;

DELIMITER $$

CREATE PROCEDURE `P_AddColumnIfNotExists`(
    IN p_db   VARCHAR(64),
    IN p_tbl  VARCHAR(64),
    IN p_col  VARCHAR(64),
    IN p_def  TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = p_db AND TABLE_NAME = p_tbl AND COLUMN_NAME = p_col
    ) THEN
        -- 不显式指定 ALGORITHM，让 MySQL 8.0 自动选择 INSTANT（列可空、无默认值、无 AFTER 时，
        -- 仅改元数据、秒级完成，避免 391 万行大表 INPLACE/COPY 重写全表）
        SET @ddl = CONCAT('ALTER TABLE `', p_tbl, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

SET @db = DATABASE();

-- ================================================================================
-- 第二部分：建同步基础表（bi_id_mapping / bi_sync_log）
-- ================================================================================
CREATE TABLE IF NOT EXISTS `bi_id_mapping` (
  `table_name` varchar(64) NOT NULL COMMENT '数据流经实体映射的基础表名',
  `offline_uuid` varchar(64) NOT NULL COMMENT '移动端或中间层分配的离线主键UUID',
  `server_id` bigint(20) NOT NULL COMMENT '服务端入库后的真实物理主键ID',
  `sync_uuid` varchar(64) DEFAULT NULL COMMENT '同步批次UUID(bi_sync_log.sync_uuid)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '该映射关系的绑定时间',
  PRIMARY KEY (`table_name`, `offline_uuid`),
  KEY `idx_server_id` (`server_id`),
  KEY `idx_idm_sync_uuid` (`sync_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离线数据上行树状拓扑重解 ID 映射图';
-- 旧库收敛：IdMappingMapper 的 insert/selectBySyncUuid 一直读写 sync_uuid，
-- 早期建表脚本漏列（2026-10-20 新链 SyncUpload E2E 暴露 Unknown column）。
CALL P_AddColumnIfNotExists(@db, 'bi_id_mapping', 'sync_uuid',
    'VARCHAR(64) NULL COMMENT ''同步批次UUID(bi_sync_log.sync_uuid)''');

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

-- ================================================================================
-- 第三部分：新标语义列（bi_object，6 列，桥跨评定引擎依赖）
-- ================================================================================
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'node_type',
    'VARCHAR(16) NULL COMMENT ''节点类型:ROOT/UNIT/SPAN/LAYER/PART/LEAF(结构对象末端)/LEGACY(旧数据占位,由代码层兜底)''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'span_no',
    'INT NULL COMMENT ''跨号,从1起;仅SPAN及跨下节点有效,其余NULL''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'bridge_type',
    'VARCHAR(16) NULL COMMENT ''桥型代码(附录B B01~B21),UNIT级绑定,SPAN继承''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'gamma',
    'DECIMAL(6,4) NULL COMMENT ''结构层影响系数γ(附录B,含0.42/0.38非整值)''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'omega',
    'INT NULL COMMENT ''部件权重ω(附录B,0~100整数,与旧weight隔离)''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'props_json',
    'TEXT NULL COMMENT ''扩展属性JSON(共用墩/全桥性构件/待确认等标记);独立于旧props(附件属性)列''');

-- ================================================================================
-- 第三·五部分：bi_object 叶子部件绑定 + 主要构件标识（JTG/T 5230—2026，2026-10-13）
--   leaf_object_id  : PART 部件节点绑定的叶子部件字典 ID（bi_leaf_object.id，建表见第五·五部分）；
--   is_main_component: 该部件是否主要构件（标准 3.3.3，按桥型区分，规则见种子 main-parts.yml）。
--   两列均 NULL、无 DEFAULT、无 AFTER，走 INSTANT；索引延后到回填后统一建（同大表铁律）。
-- ================================================================================
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'leaf_object_id',
    'BIGINT NULL COMMENT ''叶子部件字典ID(bi_leaf_object.id);仅node_type=PART节点绑定''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'is_main_component',
    'TINYINT(1) NULL COMMENT ''是否主要构件(1=主要,0=次要;仅node_type=PART有效;默认值由代码层按桥型规则main-parts.yml兜底)''');

-- ================================================================================
-- 第四部分：离线同步字段（5 张采集表）
--   核心规律（v1.6 拍板）：外键指向「下发只读数据」用整数 id 直通（不加 uuid 列）；
--                          外键指向「采集生成数据」才加 offline_uuid。
--   → bi_project / bi_task 是下发只读，不加任何 uuid 列。
--   → bi_object 不加 building_uuid；bi_disease 不加 building_uuid（整数 id 直通）。
-- ================================================================================

-- 【1】bi_object（混合：下发 UNIT + 采集 SPAN/PART）
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线UUID-4,同步唯一标识''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'parent_uuid', 'VARCHAR(64) NULL COMMENT ''父节点offline_uuid(离线树父子引用统一用UUID)''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'is_offline_data', 'TINYINT(1) NULL COMMENT ''0=云端/下发,1=离线生成(可空走INSTANT,默认值由代码层兜底)''');
CALL P_AddColumnIfNotExists(@db, 'bi_object', 'offline_deleted', 'TINYINT(1) NULL COMMENT ''0=正常,1=已删除(可空走INSTANT)''');

-- 【2】bi_element 为全新构件表（与旧 bi_component 并存），离线四列已并入第五·五部分建表语句，此处不再 ALTER。
--   旧表 bi_component 虽冻结，但 SyncUploadServiceImpl 上传链仍写离线四列；20260413/20261005
--   系列脚本未执行的库会缺列，此处幂等补齐（与基线 bi_schema CREATE 列定义一致）。
CALL P_AddColumnIfNotExists(@db, 'bi_component', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线记录唯一标识(UUID)''');
CALL P_AddColumnIfNotExists(@db, 'bi_component', 'object_uuid', 'VARCHAR(64) NULL COMMENT ''关联结构物(部件)的offline_uuid''');
CALL P_AddColumnIfNotExists(@db, 'bi_component', 'is_offline_data', 'TINYINT(1) NULL COMMENT ''0=云端,1=离线生成''');
CALL P_AddColumnIfNotExists(@db, 'bi_component', 'offline_deleted', 'TINYINT(1) NULL COMMENT ''0=正常,1=已删除''');

-- 【3】bi_disease（采集生成；挂载字段分两类：整数直通下发数据 + UUID 指向采集数据）
--   2026-10-15 修订：building_uuid 改为要加——DiseaseMapper 选择/插入列与离线 user.db
--   出包链均已使用（2026-10-05「整数 id 直通、不加 building_uuid」决策随之失效）。
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线记录唯一标识(UUID)''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'building_uuid', 'VARCHAR(64) NULL COMMENT ''关联桥梁的offline_uuid(离线包出包链使用)''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'object_uuid', 'VARCHAR(64) NULL COMMENT ''关联结构物(部件)的offline_uuid''');
-- 注意：bi_disease.component_uuid 不补。新规范已用 bi_element 取代 bi_component，
-- 出包链（selectDiseasesByBuildingIds/离线包）2026-10-16 起改读 element_uuid；
-- component_uuid 仅基线存量列，供 Web/旧上传链使用，缺列的滞后库无需补齐。
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'element_uuid', 'VARCHAR(64) NULL COMMENT ''关联构件的offline_uuid''');
-- leaf_object_id：PART 多叶子时病害显式归属的叶子部件字典ID(bi_leaf_object.id)，旧链/未指定可空
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'leaf_object_id', 'BIGINT NULL COMMENT ''病害归属叶子部件ID(bi_leaf_object.id;PART多叶子时显式指定,旧链可空)''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'is_offline_data', 'TINYINT(1) NULL COMMENT ''0=云端,1=离线生成''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'offline_deleted', 'TINYINT(1) NULL COMMENT ''0=正常,1=已删除''');

-- 【4】bi_disease_detail（采集生成）
CALL P_AddColumnIfNotExists(@db, 'bi_disease_detail', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线记录唯一标识(UUID)''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease_detail', 'disease_uuid', 'VARCHAR(64) NULL COMMENT ''关联病害的offline_uuid''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease_detail', 'is_offline_data', 'TINYINT(1) NULL COMMENT ''0=云端,1=离线生成''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease_detail', 'offline_deleted', 'TINYINT(1) NULL COMMENT ''0=正常,1=已删除''');

-- 【5】bi_attachment（采集生成）
CALL P_AddColumnIfNotExists(@db, 'bi_attachment', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线多媒体记录外标(UUID)''');
CALL P_AddColumnIfNotExists(@db, 'bi_attachment', 'offline_subject_uuid', 'VARCHAR(64) NULL COMMENT ''被挂载主体(病害等)的offline_uuid''');
CALL P_AddColumnIfNotExists(@db, 'bi_attachment', 'is_offline_data', 'TINYINT(1) NULL COMMENT ''0=云端,1=离线生成''');
CALL P_AddColumnIfNotExists(@db, 'bi_attachment', 'offline_deleted', 'TINYINT(1) NULL COMMENT ''0=正常,1=已删除''');

-- ================================================================================
-- 第五部分：全新业务表（桥跨评定 + 同步基础设施）
-- ================================================================================

-- 5.1 构件-桥跨-部件映射（共用构件跨级部件锚定，服务 BSCI 评定）
--     含 element_uuid / span_uuid / part_uuid 三个 UUID 外键列（已并入建表，无需再 ALTER）
CREATE TABLE IF NOT EXISTS `bi_span_element_part` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `element_id`  BIGINT NOT NULL COMMENT '构件ID(bi_element.id)',
  `span_id`       BIGINT NOT NULL COMMENT '桥跨节点ID(bi_object.id, node_type=SPAN)',
  `part_id`       BIGINT NOT NULL COMMENT '该构件在此跨内所属部件节点ID(bi_object.id, node_type=PART)',
  `element_uuid` VARCHAR(64) NULL COMMENT '构件离线UUID(bi_element.offline_uuid)',
  `span_uuid`      VARCHAR(64) NULL COMMENT '桥跨离线UUID(bi_object.offline_uuid, node_type=SPAN)',
  `part_uuid`      VARCHAR(64) NULL COMMENT '部件离线UUID(bi_object.offline_uuid, node_type=PART)',
  `is_shared`     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否共用构件(相邻跨均计入,1=共用)',
  `offline_uuid`  VARCHAR(64) NULL COMMENT '离线记录唯一标识(UUID)',
  `is_offline_data` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0=云端,1=离线生成',
  `offline_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0=正常,1=已删除',
  `create_by`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time`   DATETIME NULL COMMENT '创建时间',
  `update_by`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time`   DATETIME NULL COMMENT '更新时间',
  `remark`        VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_element_span_part` (`element_id`, `span_id`, `part_id`),
  KEY `idx_span` (`span_id`),
  KEY `idx_element` (`element_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='构件-桥跨-部件映射(共用构件跨级部件锚定,服务BSCI评定)';

-- 5.2 桥跨技术状况评定结果（新标准 JTG/T 5230—2026）
CREATE TABLE IF NOT EXISTS `bi_span_evaluation` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `span_id`       BIGINT NOT NULL COMMENT '桥跨节点ID(bi_object.id)',
  `task_id`       BIGINT NULL COMMENT '关联检测任务',
  `bridge_type`   VARCHAR(16) NULL COMMENT '桥型代码',
  `bsci`          DECIMAL(8,4) NULL COMMENT '桥跨技术状况指数BSCI',
  `span_level`    INT NULL COMMENT '桥跨等级(1~5)',
  `edi_score`     DECIMAL(8,4) NULL COMMENT 'EDI 分项',
  `efi_score`     DECIMAL(8,4) NULL COMMENT 'EFI 分项',
  `eai_score`     DECIMAL(8,4) NULL COMMENT 'EAI 分项',
  `std_version`   VARCHAR(16) NOT NULL DEFAULT '5230-2026' COMMENT '评定标准版本',
  `create_by`     VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time`   DATETIME NULL COMMENT '创建时间',
  `update_by`     VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time`   DATETIME NULL COMMENT '更新时间',
  `remark`        VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_span_task` (`span_id`, `task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='桥跨技术状况评定(新标准JTG/T 5230-2026)';

-- 5.3 用户 SQLite 文件引用（离线同步基础设施）
--   2026-10-15：一次建齐 SqliteService 运行期写入的全部列（package_time/update_time/
--   package_size/del_flag），user_id 改唯一键（每用户至多一条引用），避免全新环境首次
--   出包即 Unknown column；旧库由第八·七部分幂等收敛。
CREATE TABLE IF NOT EXISTS `bi_user_sqlite` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT NULL COMMENT '用户ID',
  `minio_id`      BIGINT NULL COMMENT 'SQLite文件MinIO ID',
  `package_time`  DATETIME NULL COMMENT '打包时间',
  `update_time`   DATETIME NULL COMMENT '更新时间',
  `package_size`  VARCHAR(32) NULL COMMENT '包大小(展示文本,如128 KB)',
  `del_flag`      CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志(0存在 2删除)',
  `create_time`   DATETIME NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sqlite_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户SQLite文件引用';

-- ================================================================================
-- 第五·五部分：5230 叶子部件 / 构件 字典与采集表（JTG/T 5230—2026，2026-10-13）
--   构成：
--     5.5.0 bi_element                        构件实例表（采集生成，含离线四列、material_durability_id，与旧 bi_component 并存）
--     5.5.1 bi_leaf_object                    叶子部件字典（附录B末端部件，跨桥型；bi_object.leaf_object_id 引用）
--     5.5.2 （已取消）bi_disease_position      病害位置字典——位置直接存 5.5.6.position 字符串，空表 DROP
--     5.5.3 bi_repair_strengthening_structure 维修加固结构字典（标准第13章四类）
--     5.5.4 bi_material_durability            材料耐久性分类字典（标准第4章三类）
--     5.5.5 bi_lo_disease_type                叶子部件-病害类型绑定
--     5.5.6 bi_lo_disease_position            叶子部件-病害位置（position 位置字符串 + 参考面1/2候选 ref1/ref2）
--     5.5.7 bi_rss_disease_type               维修加固结构-病害类型绑定
--     5.5.8 bi_md_disease_type                材料耐久性-病害类型绑定
--     5.5.9 bi_template_object_leaf_object    桥型模板PART节点-叶子部件多对多绑定（2026-10-16，随common.db下发）
--   约定：字典表由 Java 种子接口 POST /biz/stdSeed/run 写入，std_version 版本隔离，
--         不带离线同步列；全库不建 FOREIGN KEY，关联只靠列 + 索引。
-- ================================================================================

-- 5.5.0 构件实例（采集生成；列集对齐旧 bi_component，离线四列直接并入建表）
CREATE TABLE IF NOT EXISTS `bi_element` (
  `id`                      BIGINT NOT NULL AUTO_INCREMENT COMMENT '构件ID',
  `code`                    VARCHAR(100) NOT NULL COMMENT '构件编号',
  `name`                    VARCHAR(200) NOT NULL COMMENT '构件名称',
  `bi_object_id`            BIGINT DEFAULT NULL COMMENT '关联部件对象ID(bi_object.id,node_type=PART)',
  `material_durability_id`  BIGINT DEFAULT NULL COMMENT '材料耐久性分类ID(bi_material_durability.id;标准第4章EDDI按配筋混凝土/钢/圬工分类评定)',
  `status`                  CHAR(1) DEFAULT '0' COMMENT '构件状态(0正常 1停用)',
  `del_flag`                CHAR(1) DEFAULT '0' COMMENT '删除标志(0存在 2删除)',
  `remark`                  VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_by`               VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time`             DATETIME NULL COMMENT '创建时间',
  `update_by`               VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time`             DATETIME NULL COMMENT '更新时间',
  `offline_uuid`            VARCHAR(64) DEFAULT NULL COMMENT '离线记录唯一标识(UUID)',
  `object_uuid`             VARCHAR(64) DEFAULT NULL COMMENT '关联结构物(部件)的offline_uuid',
  `is_offline_data`         TINYINT(1) DEFAULT NULL COMMENT '0=云端,1=离线生成',
  `offline_deleted`         TINYINT(1) DEFAULT NULL COMMENT '0=正常,1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_bi_object_id` (`bi_object_id`),
  KEY `idx_material_durability_id` (`material_durability_id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='构件表(JTG/T 5230 桥梁构件element;与旧bi_component并存,旧表冻结)';

-- 5.5.1 叶子部件字典（附录B各桥型末端部件，跨桥型去重，如 bearing/slab/pierAbutment）
CREATE TABLE IF NOT EXISTS `bi_leaf_object` (
  `id`              BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`            VARCHAR(100) NOT NULL COMMENT '叶子部件编码(对齐种子parts键,如slab/bearing/pierAbutment)',
  `name`            VARCHAR(200) NOT NULL COMMENT '叶子部件名称(如:支座/桥墩或桥台/墩台基础)',
  `structure_layer` VARCHAR(32) DEFAULT NULL COMMENT '所属结构层:deck桥面系/superstructure上部结构/substructure下部结构/ancillary附属构造(附录B结构列)',
  `order_num`       INT DEFAULT 0 COMMENT '显示顺序',
  `std_version`     VARCHAR(32) NOT NULL DEFAULT '5230-2026' COMMENT '所属标准版本',
  `status`          CHAR(1) DEFAULT '0' COMMENT '状态(0正常 1停用)',
  `del_flag`        CHAR(1) DEFAULT '0' COMMENT '删除标志(0存在 2删除)',
  `remark`          VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_by`       VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time`     DATETIME NULL COMMENT '创建时间',
  `update_by`       VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time`     DATETIME NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_leaf_object_code_ver` (`code`, `std_version`),
  KEY `idx_structure_layer` (`structure_layer`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='叶子部件字典(附录B末端部件,跨桥型;桥型模板PART节点经5.5.9多对多表引用,旧bi_object.leaf_object_id单值列冻结)';

-- 5.5.2 病害位置字典：已取消（2026-10-14）
--   位置不再做独立字典，直接以字符串存于 5.5.6 bi_lo_disease_position.position；
--   早期版本（2026-10-13）曾建空表 bi_disease_position，但从未有种子/业务数据、
--   Java/App 均无引用，此处幂等删除，保持各环境结构一致。
DROP TABLE IF EXISTS `bi_disease_position`;

-- 5.5.3 维修加固结构字典（标准第13章：13.2增大截面/13.3粘贴钢板/13.4粘贴纤维复合材料/13.5增设体外预应力）
CREATE TABLE IF NOT EXISTS `bi_repair_strengthening_structure` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`        VARCHAR(100) NOT NULL COMMENT '加固结构编码',
  `name`        VARCHAR(200) NOT NULL COMMENT '加固构造名称(标准13.2~13.5四类)',
  `order_num`   INT DEFAULT 0 COMMENT '显示顺序',
  `std_version` VARCHAR(32) NOT NULL DEFAULT '5230-2026' COMMENT '所属标准版本',
  `status`      CHAR(1) DEFAULT '0' COMMENT '状态(0正常 1停用)',
  `del_flag`    CHAR(1) DEFAULT '0' COMMENT '删除标志(0存在 2删除)',
  `remark`      VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_by`   VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NULL COMMENT '创建时间',
  `update_by`   VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rss_code_ver` (`code`, `std_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修加固结构字典(标准第13章)';

-- 5.5.4 材料耐久性分类字典（标准第4章：4.1配筋混凝土构件/4.2钢构件/4.3圬工构件）
CREATE TABLE IF NOT EXISTS `bi_material_durability` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`        VARCHAR(100) NOT NULL COMMENT '材料分类编码',
  `name`        VARCHAR(200) NOT NULL COMMENT '材料耐久性分类名称(配筋混凝土构件/钢构件/圬工构件,标准第4章)',
  `order_num`   INT DEFAULT 0 COMMENT '显示顺序',
  `std_version` VARCHAR(32) NOT NULL DEFAULT '5230-2026' COMMENT '所属标准版本',
  `status`      CHAR(1) DEFAULT '0' COMMENT '状态(0正常 1停用)',
  `del_flag`    CHAR(1) DEFAULT '0' COMMENT '删除标志(0存在 2删除)',
  `remark`      VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_by`   VARCHAR(64) DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NULL COMMENT '创建时间',
  `update_by`   VARCHAR(64) DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_md_code_ver` (`code`, `std_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='材料耐久性分类字典(标准第4章)';

-- 5.5.5 叶子部件-病害类型绑定
CREATE TABLE IF NOT EXISTS `bi_lo_disease_type` (
  `id`              BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `leaf_object_id`  BIGINT NOT NULL COMMENT '叶子部件ID(bi_leaf_object.id)',
  `disease_type_id` BIGINT NOT NULL COMMENT '病害类型ID(bi_disease_type.id)',
  `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lo_disease_type` (`leaf_object_id`, `disease_type_id`),
  KEY `idx_disease_type_id` (`disease_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='叶子部件-病害类型关联表';

-- 5.5.6 叶子部件-病害位置（位置直接存字符串，附参考面1/2候选字符串；不设位置字典）
-- 结构迁移（2026-10-14）：旧版本表以 disease_position_id 关联位置字典；位置字典取消后改为
-- position 字符串列。旧表从未有种子/业务数据（Java/App 零引用），检测到旧列即 DROP 重建；
-- 已是新结构（无 disease_position_id）时本分支不触发，已配置的位置数据保留。
SET @ddl = (SELECT IF(
  EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bi_lo_disease_position'
            AND COLUMN_NAME = 'disease_position_id'),
  'DROP TABLE `bi_lo_disease_position`',
  'SELECT 1'));
PREPARE _mig_lop FROM @ddl; EXECUTE _mig_lop; DEALLOCATE PREPARE _mig_lop;
CREATE TABLE IF NOT EXISTS `bi_lo_disease_position` (
  `id`             BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `leaf_object_id` BIGINT NOT NULL COMMENT '叶子部件ID(bi_leaf_object.id)',
  `position`       VARCHAR(200) NOT NULL COMMENT '病害位置字符串(如:腹板/顶板支点附近;不再引用位置字典)',
  `ref1`           VARCHAR(500) DEFAULT NULL COMMENT '参考面1候选字符串(多个候选以逗号分隔;如:顶面,底面,腹板)',
  `ref2`           VARCHAR(500) DEFAULT NULL COMMENT '参考面2候选字符串(多个候选以逗号分隔;如:上游侧,下游侧)',
  `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lo_position` (`leaf_object_id`, `position`),
  KEY `idx_position` (`position`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='叶子部件-病害位置表(position位置字符串+参考面1/2候选)';

-- 5.5.7 维修加固结构-病害类型绑定
CREATE TABLE IF NOT EXISTS `bi_rss_disease_type` (
  `id`                                BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `repair_strengthening_structure_id` BIGINT NOT NULL COMMENT '维修加固结构ID(bi_repair_strengthening_structure.id)',
  `disease_type_id`                   BIGINT NOT NULL COMMENT '病害类型ID(bi_disease_type.id)',
  `create_time`                       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rss_disease_type` (`repair_strengthening_structure_id`, `disease_type_id`),
  KEY `idx_disease_type_id` (`disease_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修加固结构-病害类型关联表';

-- 5.5.8 材料耐久性-病害类型绑定
CREATE TABLE IF NOT EXISTS `bi_md_disease_type` (
  `id`                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_durability_id` BIGINT NOT NULL COMMENT '材料耐久性分类ID(bi_material_durability.id)',
  `disease_type_id`        BIGINT NOT NULL COMMENT '病害类型ID(bi_disease_type.id)',
  `create_time`            DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_md_disease_type` (`material_durability_id`, `disease_type_id`),
  KEY `idx_disease_type_id` (`disease_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='材料耐久性-病害类型关联表';

-- 5.5.9 桥型模板 PART 节点-叶子部件多对多绑定（2026-10-16）
--   一个模板 PART 可对应多个附录B 叶子部件；App 录入路径：实例部件节点.template_object_id
--   → bi_template_object PART → 本表 → bi_leaf_object 多叶子 → 各叶子病害类型/位置/材料绑定。
--   随 common.db 下发；首批 82 行由 5230 seed 第 13 节按 node_code 后缀(leaf code)幂等回填。
CREATE TABLE IF NOT EXISTS `bi_template_object_leaf_object` (
  `id`                 BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_object_id` BIGINT NOT NULL COMMENT '模板PART节点ID(bi_template_object.id,node_type=PART)',
  `leaf_object_id`     BIGINT NOT NULL COMMENT '叶子部件ID(bi_leaf_object.id)',
  `create_time`        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_leaf` (`template_object_id`, `leaf_object_id`),
  KEY `idx_leaf_object_id` (`leaf_object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='桥型模板PART节点-叶子部件多对多绑定(随common.db下发)';

-- ================================================================================
-- 第六部分：bi_template_object 补齐历史演进列 + 新标列
--   bi_template_object 实际完整列 = 原始12列(id/name/parent_id/ancestors/order_num/
--     status/del_flag/remark/create_by/create_time/update_by/update_time)
--     + weight + impact_factor + category_i + props + gamma + omega
--   说明：weight/impact_factor/category_i/props 是历史脚本(20260411/20260417)分散加的，
--   本合并脚本补齐，确保从「裸表」也能一把建出完整结构。
--   ★ 类型对齐（与 domain BiTemplateObject + 历史脚本一致）：
--     weight        DECIMAL(10,4)   -- domain BigDecimal，历史 seed 存 50.0000
--     impact_factor DECIMAL(10,4)   -- 20260417 脚本
--     category_i    VARCHAR(50)     -- 20260417 脚本（注意是 varchar，非 int）
--     props         VARCHAR(500)    -- domain String，存 bridgeType JSON
--     gamma         DECIMAL(6,4)    -- 与 bi_object.gamma 对齐
--     omega         INT             -- 与 bi_object.omega 对齐
--     node_type     VARCHAR(16)     -- ROOT/LAYER/PART，与 bi_object.node_type 同词汇（2026-10-14）
--     is_main_component TINYINT(1)  -- 主要构件标识（标准3.3.3，仅PART有效；2026-10-14）
-- ================================================================================
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'weight',
    'DECIMAL(10,4) NULL COMMENT ''旧标准部件权重(历史兼容)''');
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'impact_factor',
    'DECIMAL(10,4) NULL COMMENT ''旧标准影响系数Ƴ(历史兼容)''');
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'category_i',
    'VARCHAR(50) NULL COMMENT ''类别(i)''');
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'props',
    'VARCHAR(500) NULL COMMENT ''附加属性(JSON,存stdVersion/bridgeType)''');
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'gamma',
    'DECIMAL(6,4) NULL COMMENT ''结构层影响系数γ(附录B按桥型,与bi_object.gamma对齐)''');
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'omega',
    'INT NULL COMMENT ''部件权重ω(附录B,0~100整数,与bi_object.omega对齐)''');
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'node_type',
    'VARCHAR(16) NULL COMMENT ''节点类型:ROOT(桥型模板根)/LAYER(结构层)/PART(部件);与bi_object.node_type同词汇''');
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'is_main_component',
    'TINYINT(1) NULL COMMENT ''是否主要构件(标准3.3.3,1=主要,0=次要;仅PART有效,按桥型区分)''');

-- ================================================================================
-- 第七部分：bi_building 补 offline_uuid（推翻 v1.6，正/侧立面照反查需要）
--   只加 offline_uuid；不加 root_object_uuid（死字段）、不加唯一索引（回填后再说）。
-- ================================================================================
CALL P_AddColumnIfNotExists(@db, 'bi_building', 'offline_uuid',
    'VARCHAR(64) NULL COMMENT ''离线记录唯一标识(UUID)''');

-- ================================================================================
-- 第七·五部分：新规范双 object 树 + 版本隔离（JTG/T 5230—2026，2026-10-06）
--   背景：新规范与旧规范 JTG/T H21—2011 并存。
--   1) 规范版本只归属 project / task 两表（building 独立于规范，不设 std_version）。
--   2) building 通过 root_object_id（旧规范树）+ new_root_object_id（新规范树）区分双树；
--      新建新规范建筑只建新树，root_object_id 留空；旧建筑可由用户补建新规范树。
--   3) 新规范项目选检测 building 时，仅 new_root_object_id 非空的建筑才作候选。
--   ★ 与 20261006_p0_std_version_dual_tree.sql 同步（已修正：bi_building 不加 std_version）。
-- ================================================================================
CALL P_AddColumnIfNotExists(@db, 'bi_project', 'std_version',
    'VARCHAR(32) NULL COMMENT ''规范版本:5230-2026(新)/H21-2011(旧),NULL视同旧规范''');
CALL P_AddColumnIfNotExists(@db, 'bi_task', 'std_version',
    'VARCHAR(32) NULL COMMENT ''规范版本:5230-2026(新)/H21-2011(旧),NULL视同旧规范''');
CALL P_AddColumnIfNotExists(@db, 'bi_building', 'new_root_object_id',
    'BIGINT NULL COMMENT ''新规范object树根节点ID(桥跨评定单元,node_type=UNIT),旧规范建筑为NULL''');

-- 普通索引延后单独建（避免大表加列时拖慢），列存在后补非唯一索引
SET @idx_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bi_building' AND INDEX_NAME = 'idx_new_root_object_id'
);
SET @ddl_idx = IF(@idx_exists = 0,
    'ALTER TABLE `bi_building` ADD KEY `idx_new_root_object_id` (`new_root_object_id`)',
    'SELECT 1');
PREPARE stmt_idx FROM @ddl_idx; EXECUTE stmt_idx; DEALLOCATE PREPARE stmt_idx;

-- ================================================================================
-- 第八部分：旧版固定 id 种子清理（2026-10-07 起不再写入，并清理存量）
--   说明：早期批次由 SQL 直接写入 21 个根（900001~900021）与 B01 树
--         （910001~910015），它们【没有 node_code / std_version】，与 Step2 起由
--         Java 种子（StandardCatalogLoader，带 node_code + std_version=5230-2026）
--         产出的权威模板是两套并行数据。为避免重复根/重复树，本脚本：
--           1) 不再插入这些旧行（原 INSERT 已删除）；
--           2) 若库中已存在早期版本种下的这些行，则连同其病害绑定一并物理删除，
--              保证执行后只保留 Java 种子那一套。
--   安全边界：删除严格限定在本脚本历史上种下的固定 id 区间，不按名称/范围匹配，
--             不会触碰任何项目数据（bi_template_object 仅为模板，建筑引用的是
--             bi_object，不引用模板 id）。可重复执行。
-- ================================================================================

-- 8.1 先删这些模板节点上的病害类型绑定，避免关联表残留
DELETE FROM `bi_template_object_disease_type`
WHERE `template_object_id` BETWEEN 900001 AND 900021
   OR `template_object_id` BETWEEN 910001 AND 910015;

-- 8.2 再删 B01 旧树（910001~910015）与 21 个旧根（900001~900021）
DELETE FROM `bi_template_object`
WHERE `id` BETWEEN 910001 AND 910015
   OR `id` BETWEEN 900001 AND 900021;

-- ================================================================================
-- 第八·五部分：JTG/T 5230—2026 版本隔离字典列 + 复合唯一索引（Step2，2026-10-07）
--   背景：新代码的 Mapper 在常规查询中无条件 select 这些列，缺列会直接 Unknown column。
--   约定：std_version 为【可空】，NULL 视同旧标准 H21-2011（与 domain 注释一致），
--         既有 H21 行内容一律不改；新种子行显式写 '5230-2026'。
--   索引：
--     1) bi_disease_type：旧的 code 单列唯一索引 bi_disease_type_code_uindex 需 DROP，
--        改为 (code, std_version)，允许同一 code 跨版本并存。
--     2) bi_template_object：新增 (node_code, std_version) 复合唯一；存量行 node_code 为 NULL，
--        MySQL 唯一索引允许多个 NULL，不冲突。
--   幂等：列用 P_AddColumnIfNotExists；索引增/删均先查 INFORMATION_SCHEMA，可重复执行。
-- ================================================================================

-- 8.5.1 索引维护工具存储过程（存在性探测）
DROP PROCEDURE IF EXISTS `P_DropIndexIfExists`;
DROP PROCEDURE IF EXISTS `P_AddKeyIfNotExists`;

DELIMITER $$

CREATE PROCEDURE `P_DropIndexIfExists`(
    IN p_db   VARCHAR(64),
    IN p_tbl  VARCHAR(64),
    IN p_idx  VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = p_db AND TABLE_NAME = p_tbl AND INDEX_NAME = p_idx
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_tbl, '` DROP INDEX `', p_idx, '`');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END $$

CREATE PROCEDURE `P_AddKeyIfNotExists`(
    IN p_db      VARCHAR(64),
    IN p_tbl     VARCHAR(64),
    IN p_idx     VARCHAR(64),
    IN p_cols    VARCHAR(255),
    IN p_unique  VARCHAR(8)   -- 传 'UNIQUE' 或 ''
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = p_db AND TABLE_NAME = p_tbl AND INDEX_NAME = p_idx
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_tbl, '` ADD ', p_unique,
                          ' KEY `', p_idx, '` (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

-- 8.5.2 bi_disease_type：std_version 列 + 用 (code, std_version) 替换 code 单列唯一
CALL P_AddColumnIfNotExists(@db, 'bi_disease_type', 'std_version',
    'VARCHAR(32) NULL COMMENT ''所属标准版本:5230-2026/H21-2011;NULL视同旧标准H21-2011''');
CALL P_DropIndexIfExists(@db, 'bi_disease_type', 'bi_disease_type_code_uindex');
CALL P_AddKeyIfNotExists(@db, 'bi_disease_type', 'uk_disease_type_code_ver',
    '`code`, `std_version`', 'UNIQUE');

-- 8.5.3 bi_disease_scale：std_version + 结构化阈值列（为 Step3 数据驱动评定准备）
CALL P_AddColumnIfNotExists(@db, 'bi_disease_scale', 'std_version',
    'VARCHAR(32) NULL COMMENT ''所属标准版本;NULL视同旧标准H21-2011''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease_scale', 'metric_key',
    'VARCHAR(32) NULL COMMENT ''阈值度量键(如crack_width/depth/area_ratio)''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease_scale', 'value_lower',
    'DECIMAL(12,3) NULL COMMENT ''结构化区间下界(含)''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease_scale', 'value_upper',
    'DECIMAL(12,3) NULL COMMENT ''结构化区间上界(含)''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease_scale', 'unit',
    'VARCHAR(16) NULL COMMENT ''度量单位(如mm)''');

-- 8.5.4 bi_template_object：std_version + node_code + 复合唯一
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'std_version',
    'VARCHAR(32) NULL COMMENT ''所属标准版本;NULL视同旧标准H21-2011''');
CALL P_AddColumnIfNotExists(@db, 'bi_template_object', 'node_code',
    'VARCHAR(64) NULL COMMENT ''版本内自然键(版本内唯一)''');
CALL P_AddKeyIfNotExists(@db, 'bi_template_object', 'uk_template_nodecode_ver',
    '`node_code`, `std_version`', 'UNIQUE');

-- 8.5.5 bi_disease_type：标度归属指数鉴别列（EDI 缺损 / EFI 功能 / EAI 影响；缺省 EDI）
--       小字典表(约800行)，NOT NULL 常量默认值在 MySQL 8.0 走 INSTANT；
--       存量类型与现有 Java 种子(DiseaseTypeMapper 动态列插入)省略该列时自动取 EDI。
CALL P_AddColumnIfNotExists(@db, 'bi_disease_type', 'scale_type',
    'VARCHAR(16) NOT NULL DEFAULT ''EDI'' COMMENT ''标度归属指数:EDI缺损状况/EFI功能状况/EAI影响状况;缺省EDI''');

-- ================================================================================
-- 第八·六部分：JTG/T 5230—2026 三级评定引擎遗留 DDL（2026-10-14 后端整包下线）
--   [9.1] bi_span_evaluation 补 3.3.3 覆盖留痕 5 列 + (task_id,span_id) 唯一键 —— 冻结保留
--   [9.2] 引擎六表（输入三表/结果两表/bi_bridge_evaluation）已下线，幂等 DROP 收敛旧环境
-- ================================================================================

-- 9.1 bi_span_evaluation 补列（可空无默认走 INSTANT）
CALL P_AddColumnIfNotExists(@db, 'bi_span_evaluation', 'bsci_raw',
    'DECIMAL(8,4) NULL COMMENT ''3.3.3覆盖前的BSCI公式原值''');
CALL P_AddColumnIfNotExists(@db, 'bi_span_evaluation', 'raw_level',
    'INT NULL COMMENT ''覆盖前由BSCI原值判定的等级''');
CALL P_AddColumnIfNotExists(@db, 'bi_span_evaluation', 'override_flag',
    'TINYINT(1) NULL COMMENT ''是否触发3.3.3主要构件覆盖(1=是)''');
CALL P_AddColumnIfNotExists(@db, 'bi_span_evaluation', 'override_level',
    'INT NULL COMMENT ''覆盖后桥跨等级''');
CALL P_AddColumnIfNotExists(@db, 'bi_span_evaluation', 'override_trigger',
    'VARCHAR(200) NULL COMMENT ''覆盖触发构件与依据说明''');

-- 加 (task_id,span_id) 唯一键前，对存量重复行去重（保留最小 id）
DELETE t1 FROM `bi_span_evaluation` t1
JOIN `bi_span_evaluation` t2
  ON t1.`task_id` = t2.`task_id`
 AND t1.`span_id` = t2.`span_id`
 AND t1.`id` > t2.`id`;

CALL P_AddKeyIfNotExists(@db, 'bi_span_evaluation', 'uk_task_span',
    '`task_id`, `span_id`', 'UNIQUE');

-- 9.2 评定引擎六表【已下线 2026-10-14】：后端整包移除（恢复入口 git 11513368）。
--     输入三表 bi_v2_component_input / bi_v2_absent_mark / bi_v2_single_control_mark、
--     结果两表 bi_v2_component_evaluation / bi_v2_span_part_detail、全桥结果 bi_bridge_evaluation
--     在新库本就不存在；此处幂等 DROP 仅用于收敛跑过旧版脚本的环境。
--     bi_span_evaluation 的 9.1 五列与 uk_task_span 冻结保留。
DROP TABLE IF EXISTS `bi_v2_component_input`;
DROP TABLE IF EXISTS `bi_v2_absent_mark`;
DROP TABLE IF EXISTS `bi_v2_single_control_mark`;
DROP TABLE IF EXISTS `bi_v2_component_evaluation`;
DROP TABLE IF EXISTS `bi_v2_span_part_detail`;
DROP TABLE IF EXISTS `bi_bridge_evaluation`;

-- ================================================================================
-- 第八·七部分：bi_user_sqlite 收敛到运行期结构（2026-10-15）
--   旧表仅 id/user_id/minio_id/create_time + idx_user 普通索引，而 SqliteService
--   出包时写 package_time/update_time/package_size/del_flag 且按 user_id 单行 upsert。
--   全新库由 5.3 CREATE TABLE 直接得到新结构；本段幂等收敛跑过旧脚本的库：
--     1) 先对历史重复 user_id 去重（保留最小 id），再加唯一键，避免 1062；
--     2) 补齐 4 列（可空走 INSTANT），del_flag 存量行回填 '0'；
--     3) idx_user 升级为 uk_user_sqlite_user_id 唯一键。
-- ================================================================================
DELETE t1 FROM `bi_user_sqlite` t1
JOIN `bi_user_sqlite` t2
  ON t1.`user_id` = t2.`user_id`
 AND t1.`id` > t2.`id`;

CALL P_AddColumnIfNotExists(@db, 'bi_user_sqlite', 'package_time',
    'DATETIME NULL COMMENT ''打包时间''');
CALL P_AddColumnIfNotExists(@db, 'bi_user_sqlite', 'update_time',
    'DATETIME NULL COMMENT ''更新时间''');
CALL P_AddColumnIfNotExists(@db, 'bi_user_sqlite', 'package_size',
    'VARCHAR(32) NULL COMMENT ''包大小(展示文本,如128 KB)''');
CALL P_AddColumnIfNotExists(@db, 'bi_user_sqlite', 'del_flag',
    'CHAR(1) NOT NULL DEFAULT ''0'' COMMENT ''删除标志(0存在 2删除)''');
UPDATE `bi_user_sqlite` SET `del_flag` = '0' WHERE `del_flag` IS NULL;

CALL P_DropIndexIfExists(@db, 'bi_user_sqlite', 'idx_user');
CALL P_AddKeyIfNotExists(@db, 'bi_user_sqlite', 'uk_user_sqlite_user_id',
    '`user_id`', 'UNIQUE');

-- ================================================================================
-- 第八·八部分：bi_building_package 缺表补建（2026-10-15）
--   BuildingPackageMapper 的单桥病害附件 ZIP 历史表；基线脚本长期漏建，
--   POST /api/v2/building/{id}/package 出包后写历史即 Table doesn't exist。
--   每桥按 service 逻辑仅维护一行（重打包更新 minio_id/package_time）。
-- ================================================================================
CREATE TABLE IF NOT EXISTS `bi_building_package` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `building_id`   BIGINT NULL COMMENT '桥梁ID',
  `minio_id`      BIGINT NULL COMMENT 'ZIP文件MinIO ID(bi_file_map.id)',
  `package_time`  DATETIME NULL COMMENT '打包时间',
  `package_size`  VARCHAR(32) NULL COMMENT '包大小(展示文本,如0.5MB)',
  PRIMARY KEY (`id`),
  KEY `idx_building_package_building_id` (`building_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单桥病害附件包引用';

-- ================================================================================
-- 第八·九部分：bi_attachment.offline_uuid 唯一键（通道A同步幂等，2026-10-20）
--   通道A（/api/v2/sync/attachment 预上传 + sync/upload 的 attachments[]）改为按
--   offline_uuid upsert 前，历史库可能已被旧版「无条件 insert」逻辑写入重复行。
--   先按 offline_uuid 去重（保留最小 id；NULL 不参与，云端老附件无 UUID 不受影响），
--   再加唯一键兜底（MySQL 唯一索引允许多个 NULL）。全新库执行亦幂等。
-- ================================================================================
DELETE t1 FROM `bi_attachment` t1
JOIN `bi_attachment` t2
  ON t1.`offline_uuid` = t2.`offline_uuid`
 AND t1.`offline_uuid` IS NOT NULL
 AND t1.`id` > t2.`id`;

CALL P_AddKeyIfNotExists(@db, 'bi_attachment', 'uk_attachment_offline_uuid',
    '`offline_uuid`', 'UNIQUE');

-- ================================================================================
-- 第九部分：清理存储过程
-- ================================================================================
DROP PROCEDURE IF EXISTS `P_AddColumnIfNotExists`;
DROP PROCEDURE IF EXISTS `P_DropIndexIfExists`;
DROP PROCEDURE IF EXISTS `P_AddKeyIfNotExists`;

-- ================================================================================
-- 校验（可选，取消注释执行）
-- ================================================================================
-- 1) 旧固定 id 种子已清理：以下两个 COUNT 都应返回 0
-- SELECT COUNT(*) FROM bi_template_object
--  WHERE id BETWEEN 900001 AND 900021 OR id BETWEEN 910001 AND 910015;
-- SELECT COUNT(*) FROM bi_template_object_disease_type
--  WHERE template_object_id BETWEEN 900001 AND 900021
--     OR template_object_id BETWEEN 910001 AND 910015;
-- 2) 新框架模板由 Java 种子接口 POST /biz/stdSeed/run 落地后才存在：
--    21 个根（node_code='B01.root'..'B21.root'，std_version='5230-2026'）
-- SELECT COUNT(*) FROM bi_template_object
--  WHERE std_version='5230-2026' AND node_code LIKE 'B%.root';
-- 3) B01 结构层（节点码 B01.layer.*）gamma 依次 0.15/0.40/0.40/0.05
-- SELECT node_code, name, gamma FROM bi_template_object
--  WHERE std_version='5230-2026'
--    AND node_code IN ('B01.layer.deck','B01.layer.superstructure','B01.layer.substructure','B01.layer.ancillary')
--  ORDER BY gamma;
-- 4) 关键列存在性
-- SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME IN ('node_type','gamma','omega','offline_uuid','bridge_type')
--  ORDER BY TABLE_NAME, COLUMN_NAME;
-- 5) 双树 + 版本隔离：bi_project/bi_task 应有 std_version；bi_building 应有 new_root_object_id
-- SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND ((TABLE_NAME IN ('bi_project','bi_task') AND COLUMN_NAME='std_version')
--      OR (TABLE_NAME='bi_building' AND COLUMN_NAME='new_root_object_id'))
--  ORDER BY TABLE_NAME, COLUMN_NAME;
-- 6) 5230 叶子部件/构件字典 9 张新表均应存在（应返回 9 行），旧 bi_component 保持不动；
--    bi_disease_position 不应存在（第二个查询应返回 0）
-- SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('bi_element','bi_leaf_object',
--      'bi_repair_strengthening_structure','bi_material_durability',
--      'bi_lo_disease_type','bi_lo_disease_position',
--      'bi_rss_disease_type','bi_md_disease_type',
--      'bi_template_object_leaf_object')
--  ORDER BY TABLE_NAME;
-- SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bi_disease_position';
-- bi_object 应有 leaf_object_id / is_main_component 两列
-- SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bi_object'
--    AND COLUMN_NAME IN ('leaf_object_id','is_main_component')
--  ORDER BY COLUMN_NAME;
-- 7) bi_element.material_durability_id 列与索引存在
-- SELECT c.COLUMN_NAME, (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS s
--    WHERE s.TABLE_SCHEMA=c.TABLE_SCHEMA AND s.TABLE_NAME=c.TABLE_NAME
--      AND s.INDEX_NAME='idx_material_durability_id') AS idx_exists
-- FROM INFORMATION_SCHEMA.COLUMNS c
--  WHERE c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME='bi_element'
--    AND c.COLUMN_NAME='material_durability_id';
-- 8) bi_disease_type.scale_type：列存在，且存量行缺省全部为 EDI（第二个查询应返回 0）
-- SELECT COLUMN_NAME, COLUMN_DEFAULT, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
--  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bi_disease_type' AND COLUMN_NAME='scale_type';
-- SELECT COUNT(*) FROM bi_disease_type WHERE scale_type <> 'EDI' OR scale_type IS NULL;
-- 9) bi_lo_disease_position：position 为 varchar(200) NOT NULL，ref1/ref2 为 varchar(500) 可空（应返回 3 行）
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
--  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bi_lo_disease_position'
--    AND COLUMN_NAME IN ('position','ref1','ref2') ORDER BY COLUMN_NAME;
-- 10) 评定引擎六表应全部不存在（应返回 0）；bi_span_evaluation 的 5 个覆盖列与 uk_task_span 应保留
-- SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
--  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN
--    ('bi_v2_component_input','bi_v2_absent_mark','bi_v2_single_control_mark',
--     'bi_v2_component_evaluation','bi_v2_span_part_detail','bi_bridge_evaluation');
-- SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
--  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bi_span_evaluation'
--    AND COLUMN_NAME IN ('bsci_raw','raw_level','override_flag','override_level','override_trigger')
--  ORDER BY COLUMN_NAME;
-- SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS
--  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bi_span_evaluation' AND INDEX_NAME='uk_task_span';
-- 11) bi_template_object 应有 node_type / is_main_component 两列（种子执行后）：
--    ROOT=21 / LAYER=28 / PART=82；主要部件 28 个 PART 的 is_main_component=1，其余 PART=0，根/层 NULL
-- SELECT node_type, COUNT(*) FROM bi_template_object
--  WHERE std_version='5230-2026' GROUP BY node_type ORDER BY node_type;
-- SELECT is_main_component, COUNT(*) FROM bi_template_object
--  WHERE std_version='5230-2026' AND node_type='PART' GROUP BY is_main_component;
-- 12) 种子第 11 节 B01 演示树（bi_object，offline_uuid=std5230demo-b01-unit）：
--    仅 1 条 UNIT 根节点（parent_id=0、node_type='UNIT'、bridge_type='B01'）
-- SELECT node_type, bridge_type, COUNT(*) FROM bi_object
--  WHERE offline_uuid LIKE 'std5230demo-b01-%%' GROUP BY node_type, bridge_type;
-- 13) bi_user_sqlite 收敛为运行期结构（第一个查询应返回 package_time/update_time/
--    package_size/del_flag 4 行；第二个查询应返回 uk_user_sqlite_user_id；idx_user 不存在）
-- SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
--  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bi_user_sqlite'
--    AND COLUMN_NAME IN ('package_time','update_time','package_size','del_flag')
--  ORDER BY COLUMN_NAME;
-- SELECT INDEX_NAME, NON_UNIQUE FROM INFORMATION_SCHEMA.STATISTICS
--  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bi_user_sqlite'
--    AND INDEX_NAME IN ('uk_user_sqlite_user_id','idx_user');
-- 14) bi_disease.leaf_object_id 列存在且可空（IS_NULLABLE=YES；存量行值为 NULL，第二个查询应返回 0 非空）
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
--  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bi_disease' AND COLUMN_NAME='leaf_object_id';
-- SELECT COUNT(*) FROM bi_disease WHERE leaf_object_id IS NOT NULL;

-- ============================== 全量脚本结束 ==============================
