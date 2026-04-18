-- 病害位置管理菜单与权限
-- 将“病害位置管理”挂在“基础信息管理(1174)”下
SET NAMES utf8mb4;
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (1301, '病害位置管理', 1174, 6, '/biz/diseasePosition', 'menuItem', 'C', '0', '1', 'biz:diseasePosition:view', '#', 'admin', NOW(), 'admin', NOW(), '病害位置管理菜单')
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num), url = VALUES(url), perms = VALUES(perms), update_time = NOW();

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES
(1302, '病害位置查询', 1301, 1, '#', 'menuItem', 'F', '0', '1', 'biz:diseasePosition:list', '#', 'admin', NOW(), 'admin', NOW(), ''),
(1303, '病害位置新增', 1301, 2, '#', 'menuItem', 'F', '0', '1', 'biz:diseasePosition:add', '#', 'admin', NOW(), 'admin', NOW(), ''),
(1304, '病害位置修改', 1301, 3, '#', 'menuItem', 'F', '0', '1', 'biz:diseasePosition:edit', '#', 'admin', NOW(), 'admin', NOW(), ''),
(1305, '病害位置删除', 1301, 4, '#', 'menuItem', 'F', '0', '1', 'biz:diseasePosition:remove', '#', 'admin', NOW(), 'admin', NOW(), '')
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num), perms = VALUES(perms), update_time = NOW();

-- 给管理员角色授权（可按需调整角色ID）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 1301), (1, 1302), (1, 1303), (1, 1304), (1, 1305),
(2, 1301), (2, 1302), (2, 1303), (2, 1304), (2, 1305);
