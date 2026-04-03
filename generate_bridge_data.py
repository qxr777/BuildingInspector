import mysql.connector

db_config = {
    'host': '60.205.13.156',
    'user': 'root',
    'password': 'QwErTy1234%^&*()_+|',
    'database': 'bi'
}

def generate_maintenance_tree(cursor, building_name, template_id, parent_root_obj_id, is_leaf):
    print(f"Generating tree for {building_name} (Type {is_leaf})...")
    
    # 1. Fetch template root info
    cursor.execute("SELECT name, props, weight FROM bi_template_object WHERE id = %s", (template_id,))
    template_root = cursor.fetchone()
    t_name, t_props, t_weight = template_root

    # 2. Ancestors
    cursor.execute("SELECT ancestors FROM bi_object WHERE id = %s", (parent_root_obj_id,))
    p_obj = cursor.fetchone()
    p_ancestors = p_obj[0] if p_obj else "0"
    current_root_ancestors = f"{p_ancestors},{parent_root_obj_id}" if parent_root_obj_id != 0 else "0"

    # 3. Create Root BiObject
    root_obj_name = f"{building_name}({t_name})"
    cursor.execute("""
        INSERT INTO bi_object (name, parent_id, ancestors, props, weight, order_num, status, create_by, template_object_id, del_flag)
        VALUES (%s, %s, %s, %s, %s, 0, '0', 'admin', %s, '0')
    """, (root_obj_name, parent_root_obj_id, current_root_ancestors, t_props, t_weight, template_id))
    root_obj_id = cursor.lastrowid

    # 4. Create Building record
    cursor.execute("""
        INSERT INTO bi_building (name, is_leaf, area, line, root_object_id, status, create_by, del_flag)
        VALUES (%s, %s, '420100', 'G70', %s, '0', 'admin', '0')
    """, (building_name, is_leaf, root_obj_id))
    building_id = cursor.lastrowid

    # 5. Link to Project 1
    cursor.execute("INSERT INTO bi_project_building (project_id, building_id) VALUES (1, %s)", (building_id,))

    # 6. Copy children
    copy_template_recursively(cursor, template_id, root_obj_id, current_root_ancestors + "," + str(root_obj_id))
    
    return root_obj_id

def copy_template_recursively(cursor, template_parent_id, actual_parent_id, actual_ancestors):
    cursor.execute("SELECT id, name, props, weight, order_num FROM bi_template_object WHERE parent_id = %s", (template_parent_id,))
    children = cursor.fetchall()
    if not children:
        return
    for child in children:
        t_id, name, props, weight, order_num = child
        cursor.execute("""
            INSERT INTO bi_object (name, parent_id, ancestors, props, weight, order_num, status, create_by, template_object_id, del_flag)
            VALUES (%s, %s, %s, %s, %s, %s, '0', 'admin', %s, '0')
        """, (name, actual_parent_id, actual_ancestors, props, weight, order_num, t_id))
        copy_template_recursively(cursor, t_id, cursor.lastrowid, actual_ancestors + "," + str(cursor.lastrowid))

try:
    conn = mysql.connector.connect(**db_config)
    cursor = conn.cursor()
    # 宜都长江大桥 parent object ID = 110
    section_obj_id = generate_maintenance_tree(cursor, "TEST_株山湖中桥", 1, 110, '1')
    for i in range(1, 4):
        generate_maintenance_tree(cursor, f"TEST_株山湖中桥_{i}跨", 1, section_obj_id, '2')
    conn.commit()
    print("Test data fully inserted!")
    cursor.close()
    conn.close()
except Exception as e:
    print(f"Error: {e}")
