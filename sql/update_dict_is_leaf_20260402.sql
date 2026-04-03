-- ----------------------------
-- 1、增加字典类型 bi_building_is_leaf
-- ----------------------------
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, update_by, update_time, remark) 
SELECT '建筑子类类型', 'bi_building_is_leaf', '0', 'admin', sysdate(), 'admin', sysdate(), '建筑子类类型（0组合桥 1桥幅 2桥跨）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'bi_building_is_leaf');

-- ----------------------------
-- 2、增加字典数据 bi_building_is_leaf
-- ----------------------------
-- 先清除旧数据（可选，根据实际环境判断，这里采用先清后增确保一致性）
DELETE FROM sys_dict_data WHERE dict_type = 'bi_building_is_leaf';

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) 
VALUES (1, '组合桥', '0', 'bi_building_is_leaf', '', 'default', 'Y', '0', 'admin', sysdate(), 'admin', sysdate(), '组合桥');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) 
VALUES (2, '桥幅', '1', 'bi_building_is_leaf', '', 'primary', 'N', '0', 'admin', sysdate(), 'admin', sysdate(), '桥幅');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) 
VALUES (3, '桥跨', '2', 'bi_building_is_leaf', '', 'info', 'N', '0', 'admin', sysdate(), 'admin', sysdate(), '桥跨');
