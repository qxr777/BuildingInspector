-- 移动端安装包管理

CREATE TABLE IF NOT EXISTS `bi_app_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `version` varchar(64) NOT NULL COMMENT 'app版本号',
  `minio_id` bigint NOT NULL COMMENT 'MinIO文件映射ID',
  `apk_name` varchar(255) NOT NULL COMMENT 'APK文件名',
  `package_size` varchar(32) DEFAULT NULL COMMENT '安装包大小',
  `is_publish` char(1) NOT NULL DEFAULT '0' COMMENT '是否发布（1是 0否）',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  `remark` varchar(500) DEFAULT NULL COMMENT '更新备注',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_bi_app_package_publish` (`is_publish`, `del_flag`),
  KEY `idx_bi_app_package_minio` (`minio_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='移动端安装包';

SET @appParentId := (SELECT menu_id FROM sys_menu WHERE menu_name = '基础信息管理' AND menu_type = 'M' ORDER BY menu_id LIMIT 1);

INSERT INTO sys_menu
(`menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '移动端安装包', @appParentId, 6, '/biz/appPackage', 'menuItem', 'C', '0', '1', 'biz:appPackage:view', '#', 'admin', sysdate(), '', NULL, '移动端安装包菜单'
WHERE @appParentId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'biz:appPackage:view');

SET @appPackageMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'biz:appPackage:view' ORDER BY menu_id LIMIT 1);

INSERT INTO sys_menu
(`menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '移动端安装包查询', @appPackageMenuId, 1, '#', '', 'F', '0', '1', 'biz:appPackage:list', '#', 'admin', sysdate(), '', NULL, ''
WHERE @appPackageMenuId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'biz:appPackage:list');

INSERT INTO sys_menu
(`menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '移动端安装包新增', @appPackageMenuId, 2, '#', '', 'F', '0', '1', 'biz:appPackage:add', '#', 'admin', sysdate(), '', NULL, ''
WHERE @appPackageMenuId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'biz:appPackage:add');

INSERT INTO sys_menu
(`menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '移动端安装包修改', @appPackageMenuId, 3, '#', '', 'F', '0', '1', 'biz:appPackage:edit', '#', 'admin', sysdate(), '', NULL, ''
WHERE @appPackageMenuId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'biz:appPackage:edit');

INSERT INTO sys_menu
(`menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '移动端安装包删除', @appPackageMenuId, 4, '#', '', 'F', '0', '1', 'biz:appPackage:remove', '#', 'admin', sysdate(), '', NULL, ''
WHERE @appPackageMenuId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'biz:appPackage:remove');

INSERT INTO sys_menu
(`menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '移动端安装包下载', @appPackageMenuId, 5, '#', '', 'F', '0', '1', 'biz:appPackage:download', '#', 'admin', sysdate(), '', NULL, ''
WHERE @appPackageMenuId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'biz:appPackage:download');
