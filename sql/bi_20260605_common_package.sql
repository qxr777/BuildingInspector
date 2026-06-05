-- 公共数据包管理菜单

SET @commonPackageParentId := (SELECT menu_id FROM sys_menu WHERE menu_name = '基础信息管理' AND menu_type = 'M' ORDER BY menu_id LIMIT 1);

INSERT INTO sys_menu
(`menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '公共数据包', @commonPackageParentId, 7, '/biz/commonPackage', 'menuItem', 'C', '0', '1', 'biz:commonPackage:view', '#', 'admin', sysdate(), '', NULL, '公共数据包菜单'
WHERE @commonPackageParentId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'biz:commonPackage:view');

SET @commonPackageMenuId := (SELECT menu_id FROM sys_menu WHERE perms = 'biz:commonPackage:view' ORDER BY menu_id LIMIT 1);

INSERT INTO sys_menu
(`menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '公共数据包查询', @commonPackageMenuId, 1, '#', '', 'F', '0', '1', 'biz:commonPackage:list', '#', 'admin', sysdate(), '', NULL, ''
WHERE @commonPackageMenuId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'biz:commonPackage:list');

INSERT INTO sys_menu
(`menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '公共数据包生成', @commonPackageMenuId, 2, '#', '', 'F', '0', '1', 'biz:commonPackage:add', '#', 'admin', sysdate(), '', NULL, ''
WHERE @commonPackageMenuId IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'biz:commonPackage:add');
