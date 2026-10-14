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
--   ※ 新模板数据不再由 SQL 写入，统一改由 Java 种子接口 POST /biz/stdSeed/run 落地。
--
-- 合并决策（2026-10-05）：
--   1. 以 v1.6 决策 + 最终 P1 拍板为准，【去掉旧脚本中被 v1.6 否决的内容】：
--      - bi_building 不加 root_object_uuid、不加唯一索引 idx_building_uuid（只加 offline_uuid）；
--      - bi_object 不加 building_uuid；bi_disease 不加 building_uuid（整数 id 直通）。
--   2. 唯一索引【全部延后】到回填后（本脚本不含回填），避免存量 NULL 冲突。
--   3. 不含 P2 回填（用户明确排除耗时回填）。
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
--   [3] 建 3 张新表 bi_span_component_part / bi_span_evaluation / bi_user_sqlite
--   [4] bi_template_object 补 gamma/omega
--   [5] bi_building 补 offline_uuid
--   [6] 种子数据：21 桥型顶层 + B01 完整树
--   [7] 版本隔离字典列 + 复合唯一索引（bi_disease_type / bi_disease_scale / bi_template_object）
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
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '该映射关系的绑定时间',
  PRIMARY KEY (`table_name`, `offline_uuid`),
  KEY `idx_server_id` (`server_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离线数据上行树状拓扑重解 ID 映射图';

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

-- 【2】bi_component（采集生成）
CALL P_AddColumnIfNotExists(@db, 'bi_component', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线记录唯一标识(UUID)''');
CALL P_AddColumnIfNotExists(@db, 'bi_component', 'object_uuid', 'VARCHAR(64) NULL COMMENT ''关联结构物(部件)的offline_uuid''');
CALL P_AddColumnIfNotExists(@db, 'bi_component', 'is_offline_data', 'TINYINT(1) NULL COMMENT ''0=云端,1=离线生成''');
CALL P_AddColumnIfNotExists(@db, 'bi_component', 'offline_deleted', 'TINYINT(1) NULL COMMENT ''0=正常,1=已删除''');

-- 【3】bi_disease（采集生成；挂载字段分两类：整数直通下发数据 + UUID 指向采集数据）
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'offline_uuid', 'VARCHAR(64) NULL COMMENT ''离线记录唯一标识(UUID)''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'object_uuid', 'VARCHAR(64) NULL COMMENT ''关联结构物(部件)的offline_uuid''');
CALL P_AddColumnIfNotExists(@db, 'bi_disease', 'component_uuid', 'VARCHAR(64) NULL COMMENT ''关联构件的offline_uuid''');
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
--     含 component_uuid / span_uuid / part_uuid 三个 UUID 外键列（已并入建表，无需再 ALTER）
CREATE TABLE IF NOT EXISTS `bi_span_component_part` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `component_id`  BIGINT NOT NULL COMMENT '构件ID(bi_component.id)',
  `span_id`       BIGINT NOT NULL COMMENT '桥跨节点ID(bi_object.id, node_type=SPAN)',
  `part_id`       BIGINT NOT NULL COMMENT '该构件在此跨内所属部件节点ID(bi_object.id, node_type=PART)',
  `component_uuid` VARCHAR(64) NULL COMMENT '构件离线UUID(bi_component.offline_uuid)',
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
  UNIQUE KEY `uk_component_span_part` (`component_id`, `span_id`, `part_id`),
  KEY `idx_span` (`span_id`),
  KEY `idx_component` (`component_id`)
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
CREATE TABLE IF NOT EXISTS `bi_user_sqlite` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT NULL COMMENT '用户ID',
  `minio_id`      BIGINT NULL COMMENT 'SQLite文件MinIO ID',
  `create_time`   DATETIME NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户SQLite文件引用';

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

-- ============================== 全量脚本结束 ==============================
