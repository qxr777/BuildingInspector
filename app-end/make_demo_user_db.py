#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 App 现场离线作业两个阶段的 user.db 样例（与 E2E-APP-001 / AppOfflineWorkflowE2ETest 同构）。

业务边界：桥梁本体不在现场创建。ROOT/UNIT、bi_building、5230 任务均由云端建桥/任务下发
产生并随离线包下发（server_id 有值、is_offline_data=0）；现场只在下发的 UNIT 下新增
SPAN/LAYER/PART 以及构件页数据，sync/upload 的 buildings 恒为空数组。

产出（与本脚本同目录）：
  user_stage1_span_part_added.db   第一批同步时刻：下载包 + 现场增补 2单元×2跨(含层/部件)
  user_stage2_assessment_inputs.db 第二批同步时刻：结构行已随刷新包转为服务端行(server_id 回填、
                                   is_offline_data=0)，现场再录构件/病害/详情/照片/人工判定

DDL 逐字摘自 SqliteService（bi-biz，用户包 14 张表：createAllTables 13 张 + doGenerateUserSqlite
另含 bi_property）；ID 为便于阅读的固定演示值，
同步身份键是 offline_uuid 而非自增 ID。
"""
import os
import sqlite3

HERE = os.path.dirname(os.path.abspath(__file__))

DDL = [
    "CREATE TABLE IF NOT EXISTS bi_project (id INTEGER PRIMARY KEY, name TEXT, year INTEGER, status TEXT, code TEXT, start_date TEXT, end_date TEXT, std_version TEXT)",
    "CREATE TABLE IF NOT EXISTS bi_task (id INTEGER PRIMARY KEY, building_id INTEGER, project_id INTEGER, status TEXT, evaluation_result INTEGER, type INTEGER, std_version TEXT, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT)",
    "CREATE TABLE IF NOT EXISTS bi_building (id INTEGER PRIMARY KEY, name TEXT, is_leaf TEXT, status TEXT, del_flag TEXT, longitude REAL, latitude REAL, altitude REAL, address TEXT, area TEXT, line TEXT, admin_dept TEXT, weight REAL, video_feed TEXT, root_object_id INTEGER, new_root_object_id INTEGER, root_property_id INTEGER, remark TEXT, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, offline_uuid TEXT, offline_deleted INTEGER DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS bi_object (offline_uuid TEXT PRIMARY KEY, server_id INTEGER, parent_id INTEGER, name TEXT, ancestors TEXT, status TEXT, del_flag TEXT, longitude REAL, latitude REAL, altitude REAL, position TEXT, area TEXT, admin_dept TEXT, weight REAL, standard_weight REAL, video_feed TEXT, props TEXT, template_object_id INTEGER, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT, parent_uuid TEXT, is_offline_data INTEGER DEFAULT 0, node_type TEXT, bridge_type TEXT, gamma REAL, omega INTEGER, props_json TEXT, span_no INTEGER, is_main_component INTEGER, offline_deleted INTEGER DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS bi_element (offline_uuid TEXT PRIMARY KEY, server_id INTEGER, bi_object_id INTEGER, material_durability_id INTEGER, name TEXT, code TEXT, status TEXT, del_flag TEXT, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT, object_uuid TEXT, is_offline_data INTEGER DEFAULT 0, offline_deleted INTEGER DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS bi_disease (offline_uuid TEXT PRIMARY KEY, server_id INTEGER, position TEXT, position_number TEXT, type TEXT, disease_type_id INTEGER, description TEXT, level TEXT, quantity TEXT, units TEXT, nature TEXT, participate_assess TEXT, deduct_points INTEGER, img_no_exp TEXT, project_id INTEGER, bi_object_id INTEGER, bi_object_name TEXT, building_id INTEGER, component_id INTEGER, commit_type TEXT, local_id TEXT, remark TEXT, cause TEXT, repair_recommendation TEXT, crack_type TEXT, development_trend TEXT, detection_method TEXT, attachment_count INTEGER, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, task_id INTEGER, building_uuid TEXT, object_uuid TEXT, element_uuid TEXT, leaf_object_id INTEGER, is_offline_data INTEGER DEFAULT 0, offline_deleted INTEGER DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS bi_file_map (id INTEGER PRIMARY KEY, old_name TEXT, new_name TEXT, create_time TEXT, update_time TEXT, create_by TEXT, file_type TEXT)",
    "CREATE TABLE IF NOT EXISTS bi_attachment (offline_uuid TEXT PRIMARY KEY, server_id INTEGER, name TEXT, subject_id INTEGER, type INTEGER, del_flag TEXT, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, weight REAL, minio_id INTEGER, thumb_minio_id INTEGER, offline_subject_uuid TEXT, is_offline_data INTEGER DEFAULT 0, offline_deleted INTEGER DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS bi_disease_detail (offline_uuid TEXT PRIMARY KEY, server_id INTEGER, disease_id INTEGER, reference1_location TEXT, reference1_location_start REAL, reference1_location_end REAL, reference2_location TEXT, reference2_location_start REAL, reference2_location_end REAL, length1 REAL, length2 REAL, length3 REAL, width REAL, height_depth REAL, crack_width REAL, area_length REAL, area_width REAL, area_identifier INTEGER, deformation REAL, angle INTEGER, numerator_ratio INTEGER, denominator_ratio INTEGER, length_range_start REAL, length_range_end REAL, width_range_start REAL, width_range_end REAL, height_depth_range_start REAL, height_depth_range_end REAL, crack_width_range_start REAL, crack_width_range_end REAL, area_range_start REAL, area_range_end REAL, deformation_range_start REAL, deformation_range_end REAL, angle_range_start REAL, angle_range_end REAL, other TEXT, disease_uuid TEXT, is_offline_data INTEGER DEFAULT 0, offline_deleted INTEGER DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS bi_span_element_part (offline_uuid TEXT PRIMARY KEY, server_id INTEGER, element_id INTEGER, span_id INTEGER, part_id INTEGER, element_uuid TEXT, span_uuid TEXT, part_uuid TEXT, is_shared INTEGER DEFAULT 0, is_offline_data INTEGER DEFAULT 0, offline_deleted INTEGER DEFAULT 0, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT)",
    "CREATE TABLE IF NOT EXISTS bi_property (id INTEGER PRIMARY KEY, name TEXT, value TEXT, parent_id INTEGER, ancestors TEXT, order_num INTEGER, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT)",
    "CREATE TABLE IF NOT EXISTS bi_assess_manual_input (offline_uuid TEXT PRIMARY KEY, server_id INTEGER, task_id INTEGER, element_id INTEGER, element_uuid TEXT, eddi INTEGER, esdi_override INTEGER, efi INTEGER, eai INTEGER, safety_affected INTEGER, evidence TEXT, is_offline_data INTEGER DEFAULT 0, offline_deleted INTEGER DEFAULT 0, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT)",
    "CREATE TABLE IF NOT EXISTS bi_assess_absent_mark (offline_uuid TEXT PRIMARY KEY, server_id INTEGER, task_id INTEGER, unit_id INTEGER, unit_uuid TEXT, span_id INTEGER, span_uuid TEXT, part_key TEXT, layer_key TEXT, evidence TEXT, is_offline_data INTEGER DEFAULT 0, offline_deleted INTEGER DEFAULT 0, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT)",
    "CREATE TABLE IF NOT EXISTS bi_assess_single_control_mark (offline_uuid TEXT PRIMARY KEY, server_id INTEGER, task_id INTEGER, indicator_no INTEGER, hit INTEGER, span_id INTEGER, span_uuid TEXT, element_id INTEGER, element_uuid TEXT, evidence TEXT, is_offline_data INTEGER DEFAULT 0, offline_deleted INTEGER DEFAULT 0, create_by TEXT, create_time TEXT, update_by TEXT, update_time TEXT, remark TEXT)",
]

# ---- 稳定演示身份（同步只认 offline_uuid；ID 仅便于阅读） ----
BUILDING_ID, TASK_ID = 9101, 9101
ROOT_ID, U1_ID, U2_ID = 9201, 9202, 9203
# 现场增补 6 个结构节点的服务端 ID（第一批同步后由云端分配，刷新包带回）
S1_ID, S2_ID, S3_ID, S4_ID, LAYER_ID, SLAB_ID = 9204, 9205, 9206, 9207, 9208, 9209
EDI_TYPE_ID = 9301          # 云端字典中的 5230/EDI 病害类型（演示值）
MINIO_ID, THUMB_ID = 9401, 9402  # 预上传返回的原图/缩略图 bi_file_map id（演示值）

B = "appdemo-building"
ROOT, U1, U2 = "appdemo-root", "appdemo-unit1", "appdemo-unit2"
S1, S2, S3, S4 = "appdemo-u1span1", "appdemo-u1span2", "appdemo-u2span1", "appdemo-u2span2"
LAYER, SLAB = "appdemo-layer", "appdemo-slab"
ELEM, ANCHOR = "appdemo-element", "appdemo-anchor"
DIS, DET, ATT = "appdemo-disease", "appdemo-detail", "appdemo-photo"
MANUAL, ABSENT = "appdemo-manual", "appdemo-absent"

TS = "2026-10-27 15:00:00"


def init_db(path):
    if os.path.exists(path):
        os.remove(path)
    conn = sqlite3.connect(path)
    for ddl in DDL:
        conn.execute(ddl)
    return conn


def _insert_downloaded_objects(conn):
    """清晰版：下发行三个节点（与 seed_downloaded_package 分开调用）。"""
    rows = [
        (ROOT, ROOT_ID, 0, "", "全桥根节点", "ROOT", None, None),
        (U1, U1_ID, ROOT_ID, ROOT, "评定单元一", "UNIT", "B01", None),
        (U2, U2_ID, ROOT_ID, ROOT, "评定单元二", "UNIT", "B01", None),
    ]
    conn.executemany(
        "INSERT INTO bi_object (offline_uuid,server_id,parent_id,parent_uuid,name,node_type,"
        "bridge_type,span_no,status,is_offline_data,offline_deleted,create_by,create_time) "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
        [(u, s, p, pu, n, t, bt, sn, "0", 0, 0, "web-admin", TS)
         for (u, s, p, pu, n, t, bt, sn) in rows])


def add_field_structure(conn, flagged_offline, server_ids):
    """现场在下发的 UNIT 下乱序增补：4 SPAN + 1 LAYER + 1 PART。

    flagged_offline=True：本地新建（server_id=NULL、is_offline_data=1，第一批同步上传）；
    flagged_offline=False：第一批同步后的刷新包形态（server_id 已回填、is_offline_data=0）。
    """
    flag = 1 if flagged_offline else 0
    # 刷新包形态下 server_id 与服务端 parent_id 均已回填（对应 insertBiObjects 出包列）
    sid_of = {S1: S1_ID, S2: S2_ID, S3: S3_ID, S4: S4_ID, LAYER: LAYER_ID, SLAB: SLAB_ID}
    parent_id_of = {U1: U1_ID, U2: U2_ID, S1: S1_ID, LAYER: LAYER_ID}
    rows = [
        # offline_uuid, server_id, parent_id, parent_uuid, name, node_type, bridge_type,
        # span_no, gamma, omega, props_json
        (SLAB, LAYER, "实心板", "PART", None, None, None, 75, '{"part":"slab"}'),
        (S1, U1, "第1跨", "SPAN", "B01", 1, None, None, None),
        (S2, U1, "第2跨", "SPAN", "B01", 2, None, None, None),
        (S3, U2, "第1跨", "SPAN", "B01", 1, None, None, None),
        (S4, U2, "第2跨", "SPAN", "B01", 2, None, None, None),
        (LAYER, S1, "上部结构", "LAYER", None, None, 0.40, None,
         '{"layer":"superstructure"}'),
    ]
    expanded = []
    for uuid, parent_uuid, name, node_type, bt, span_no, gamma, omega, pj in rows:
        sid = sid_of[uuid] if server_ids else None
        pid = parent_id_of[parent_uuid] if server_ids else None
        expanded.append((uuid, sid, pid, parent_uuid, name, node_type, bt,
                         span_no, gamma, omega, pj))
    rows = expanded
    conn.executemany(
        "INSERT INTO bi_object (offline_uuid,server_id,parent_id,parent_uuid,name,node_type,"
        "bridge_type,span_no,gamma,omega,props_json,status,is_offline_data,offline_deleted,"
        "create_by,create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        [(u, s, p, pu, n, t, bt, sn, g, w, pj, "0", flag, 0, "app-user", TS)
         for (u, s, p, pu, n, t, bt, sn, g, w, pj) in rows])


def add_element_page_inputs(conn):
    """构件 Element 页录入（第二批同步上传，全部 is_offline_data=1）。"""
    conn.execute(
        "INSERT INTO bi_element (offline_uuid,code,name,object_uuid,status,is_offline_data,"
        "offline_deleted,create_by,create_time) VALUES (?,?,?,?,?,?,?,?,?)",
        (ELEM, "EL-APP-1", "1号板", SLAB, "0", 1, 0, "app-user", TS))
    conn.execute(
        "INSERT INTO bi_span_element_part (offline_uuid,element_uuid,span_uuid,part_uuid,"
        "is_shared,is_offline_data,offline_deleted,create_by,create_time) VALUES (?,?,?,?,?,?,?,?,?)",
        (ANCHOR, ELEM, S1, SLAB, 0, 1, 0, "app-user", TS))
    conn.execute(
        "INSERT INTO bi_disease (offline_uuid,task_id,object_uuid,element_uuid,disease_type_id,"
        "type,description,level,quantity,participate_assess,attachment_count,is_offline_data,"
        "offline_deleted,create_by,create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        (DIS, TASK_ID, SLAB, ELEM, EDI_TYPE_ID, "结构性裂缝", "App现场记录：板底横向裂缝",
         "2", "1", "1", 0, 1, 0, "app-user", TS))
    conn.execute(
        "INSERT INTO bi_disease_detail (offline_uuid,disease_uuid,length1,width,is_offline_data,"
        "offline_deleted) VALUES (?,?,?,?,?,?)",
        (DET, DIS, 1.2, 0.25, 1, 0))
    conn.execute(
        "INSERT INTO bi_attachment (offline_uuid,name,type,minio_id,thumb_minio_id,"
        "offline_subject_uuid,is_offline_data,offline_deleted,create_by,create_time) "
        "VALUES (?,?,?,?,?,?,?,?,?,?)",
        (ATT, "crack-appdemo.jpg", 1, MINIO_ID, THUMB_ID, DIS, 1, 0, "app-user", TS))
    conn.execute(
        "INSERT INTO bi_assess_manual_input (offline_uuid,task_id,element_uuid,efi,"
        "is_offline_data,offline_deleted,create_by,create_time) VALUES (?,?,?,?,?,?,?,?)",
        (MANUAL, TASK_ID, ELEM, 1, 1, 0, "app-user", TS))
    conn.execute(
        "INSERT INTO bi_assess_absent_mark (offline_uuid,task_id,unit_uuid,span_uuid,part_key,"
        "layer_key,is_offline_data,offline_deleted,create_by,create_time) VALUES (?,?,?,?,?,?,?,?,?,?)",
        (ABSENT, TASK_ID, U1, S1, "bearing", "superstructure", 1, 0, "app-user", TS))


def build_stage1(path):
    conn = init_db(path)
    conn.execute(
        "INSERT INTO bi_building (id,name,is_leaf,status,del_flag,root_object_id,new_root_object_id,"
        "offline_uuid,offline_deleted,create_by,create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        (BUILDING_ID, "App现场检查桥", "1", "0", "0", ROOT_ID, ROOT_ID, B, 0, "web-admin", TS))
    conn.execute(
        "INSERT INTO bi_task (id,building_id,status,std_version,create_by,create_time) VALUES (?,?,?,?,?,?)",
        (TASK_ID, BUILDING_ID, "0", "5230-2026", "web-admin", TS))
    _insert_downloaded_objects(conn)
    add_field_structure(conn, flagged_offline=True, server_ids=False)
    conn.commit()
    conn.close()


def build_stage2(path):
    conn = init_db(path)
    # 刷新包：桥梁/任务/ROOT/UNIT 仍为下发行；第一批同步的 6 个结构节点已转为服务端行
    conn.execute(
        "INSERT INTO bi_building (id,name,is_leaf,status,del_flag,root_object_id,new_root_object_id,"
        "offline_uuid,offline_deleted,create_by,create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        (BUILDING_ID, "App现场检查桥", "1", "0", "0", ROOT_ID, ROOT_ID, B, 0, "web-admin", TS))
    conn.execute(
        "INSERT INTO bi_task (id,building_id,status,std_version,create_by,create_time) VALUES (?,?,?,?,?,?)",
        (TASK_ID, BUILDING_ID, "0", "5230-2026", "web-admin", TS))
    _insert_downloaded_objects(conn)
    add_field_structure(conn, flagged_offline=False, server_ids=True)
    # 第二批同步前的现场新增：构件页全部输入（is_offline_data=1）
    add_element_page_inputs(conn)
    conn.commit()
    conn.close()


if __name__ == "__main__":
    p1 = os.path.join(HERE, "user_stage1_span_part_added.db")
    p2 = os.path.join(HERE, "user_stage2_assessment_inputs.db")
    build_stage1(p1)
    build_stage2(p2)
    print("generated:", p1)
    print("generated:", p2)
