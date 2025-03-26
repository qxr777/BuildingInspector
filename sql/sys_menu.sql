# 修改菜单名称---桥梁基础信息
UPDATE bi.sys_menu
SET
    menu_name = '桥梁基础信息',
    parent_id = 1174,
    order_num = 1,
    url = '/biz/property',
    target = 'menuItem',
    menu_type = 'C',
    visible = '0',
    is_refresh = '1',
    perms = 'biz:property:view',
    icon = '#',
    create_by = 'admin',
    create_time = '2025-03-15 21:16:17',
    update_by = 'admin',
    update_time = '2025-03-15 21:21:27',
    remark = '桥梁信息管理'
WHERE menu_id = 1175;

INSERT INTO bi.sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES ('桥梁属性查询', 1175, 1, '#', '', 'F', '0', '1', 'biz:property:list', '#', 'admin', '2025-03-21 10:06:22', '', null, '');
INSERT INTO bi.sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES ('桥梁属性新增', 1175, 2, '#', '', 'F', '0', '1', 'biz:property:add', '#', 'admin', '2025-03-21 10:06:22', '', null, '');
INSERT INTO bi.sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES ('桥梁属性修改', 1175, 3, '#', '', 'F', '0', '1', 'biz:property:edit', '#', 'admin', '2025-03-21 10:06:22', '', null, '');
INSERT INTO bi.sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES ('桥梁属性删除', 1175, 4, '#', '', 'F', '0', '1', 'biz:property:remove', '#', 'admin', '2025-03-21 10:06:22', '', null, '');
INSERT INTO bi.sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES ('桥梁属性导出', 1175, 5, '#', '', 'F', '0', '1', 'biz:property:export', '#', 'admin', '2025-03-21 10:06:22', '', null, '');
INSERT INTO bi.sys_menu (menu_name, parent_id, order_num, url, target, menu_type, visible, is_refresh, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES ('桥梁属性json导入', 1175, 6, '#', '', 'F', '0', '1', 'biz:property:add', '#', 'admin', '2025-03-21 10:06:22', '', null, '');
