import sqlite3
import json
import uuid
import requests
import os
import sys

# 配置区
BASE_URL = "http://localhost:80"  # 根据你的实际端口修改
SQLITE_FILE = "u95.db"
USER_ID = 95
CLIENT_INFO = "Python Automation Test Tool v1.0"
AUTH_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3NzU4MDc3MDQsInVzZXJuYW1lIjoiem5qY190ZXN0XzEifQ.qcttii7GbtjG-1lhUwuj2NJMcVWVOwv11LB5mUCp3hQ"

def get_db_connection():
    conn = sqlite3.connect(SQLITE_FILE)
    conn.row_factory = sqlite3.Row
    return conn

def upload_attachment(dummy_name):
    """模拟 App 上传附件"""
    url = f"{BASE_URL}/api/v2/sync/attachment"
    # 创建一个简单的 dummy 图片文件内容
    files = {'file': (dummy_name, b'dummy_image_content', 'image/jpeg')}
    headers = {'Authorization': AUTH_TOKEN}
    try:
        print(f"  [+] 正在上传附件: {dummy_name} ...")
        resp = requests.post(url, files=files, headers=headers)
        if resp.status_code == 200:
            res_json = resp.json()
            if res_json.get('code') == 0 or res_json.get('code') == 200:
                server_id = res_json.get('data')
                print(f"  [√] 上传成功, ServerID: {server_id}")
                return server_id
        print(f"  [X] 上传失败: {resp.text}")
    except Exception as e:
        print(f"  [X] 上传异常: {e}")
    return None

def mock_offline_data(conn):
    """在 SQLite 中插入几条模拟的离线新增数据 (适配 u95.db 真实列名)"""
    cursor = conn.cursor()
    # 确保 bi_disease_detail 表结构最新
    required_cols = [
        ("disease_uuid", "TEXT"),
        ("step", "INTEGER"),
        ("offline_uuid", "TEXT"),
        ("is_offline_data", "INTEGER")
    ]
    for col_name, col_type in required_cols:
        try:
            cursor.execute(f"ALTER TABLE bi_disease_detail ADD COLUMN {col_name} {col_type}")
        except sqlite3.OperationalError:
            pass # 已存在

    # 1. 模拟新增一个部件 (Object)
    cursor.execute("SELECT offline_uuid FROM bi_building LIMIT 1")
    building_row = cursor.fetchone()
    building_uuid = building_row[0] if building_row else "mock-building-uuid"
    
    obj_uuid = "test-mock-obj-" + str(uuid.uuid4())[:8]
    print(f"  [+] 构造模拟部件: {obj_uuid}")
    # u95.db 中的列名为 offline_parent_uuid
    cursor.execute("""
        INSERT INTO bi_object (name, is_offline_data, offline_uuid, status, parent_id)
        VALUES (?, 1, ?, '0', 0)
    """, ("自动化测试模拟部件", obj_uuid))
    
    # 2. 模拟为一个病害 (Disease)
    dis_uuid = "test-mock-dis-" + str(uuid.uuid4())[:8]
    print(f"  [+] 构造模拟病害: {dis_uuid}")
    # bi_disease 列名为 offline_object_uuid, offline_building_uuid
    cursor.execute("""
        INSERT INTO bi_disease (description, offline_object_uuid, offline_building_uuid, is_offline_data, offline_uuid, type)
        VALUES (?, ?, ?, 1, ?, ?)
    """, ("模拟裂缝病害-由脚本生成", obj_uuid, building_uuid, dis_uuid, "1"))
    
    # 3. 模拟为一个详情 (DiseaseDetail)
    det_uuid = "test-mock-det-" + str(uuid.uuid4())[:8]
    print(f"  [+] 构造模拟详情: {det_uuid}")
    cursor.execute("""
        INSERT INTO bi_disease_detail (offline_uuid, disease_uuid, step, is_offline_data)
        VALUES (?, ?, ?, 1)
    """, (det_uuid, dis_uuid, 1))

    conn.commit()

def fetch_table_data(conn, table_name):
    # 下面定义字段名转换映射，适配后端实体类属性名
    field_mapping = {
        'offline_uuid': 'offlineUuid',
        'offline_parent_uuid': 'parentUuid',
        'offline_building_uuid': 'buildingUuid',
        'offline_object_uuid': 'objectUuid',
        'offline_component_uuid': 'componentUuid',
        'disease_uuid': 'diseaseUuid',
        'is_offline_data': 'isOfflineData'
    }

    cursor = conn.cursor()
    cursor.execute(f"SELECT * FROM {table_name} WHERE is_offline_data = 1")
    rows = cursor.fetchall()
    data = []
    
    for row in rows:
        raw_item = dict(row)
        mapped_item = {}
        for k, v in raw_item.items():
            # 自动映射字段名，如果不在映射表中则保留原样
            new_key = field_mapping.get(k, k)
            mapped_item[new_key] = v
        data.append(mapped_item)
    return data

def main():
    if not os.path.exists(SQLITE_FILE):
        print(f"错误: 找不到 {SQLITE_FILE} 文件，请确保它在当前目录。")
        sys.exit(1)

    print(f"=== 启动离线同步自动化测试 (Target: {BASE_URL}) ===")
    
    conn = get_db_connection()
    try:
        # 0. 构造模拟数据（模拟移动端新增）
        print("\n0. [构造模拟离线数据]...")
        mock_offline_data(conn)

        # 1. 抽取附件数据并模拟上传
        print("\n1. [处理附件上传]...")
        # 注意：这里我们只模拟上传属于离线记录的附件
        attachments = fetch_table_data(conn, "bi_attachment")
        for attr in attachments:
            server_id = upload_attachment(attr.get('name') or "test_offline.jpg")
            if server_id:
                attr['minioId'] = server_id
        
        # 2. 提取业务数据 (仅限 is_offline_data = 1)
        print("\n2. [提取增量业务数据]...")
        payload = {
            "syncUuid": str(uuid.uuid4()),
            "userId": USER_ID,
            "clientInfo": CLIENT_INFO,
            "buildings": fetch_table_data(conn, "bi_building"),
            "objects": fetch_table_data(conn, "bi_object"),
            "components": fetch_table_data(conn, "bi_component"),
            "diseases": fetch_table_data(conn, "bi_disease"),
            "diseaseDetails": fetch_table_data(conn, "bi_disease_detail"),
            "attachments": attachments
        }
        
        # 统计发送条数
        total_counts = sum(len(v) for k, v in payload.items() if isinstance(v, list))
        print(f"  本次待同步记录总数: {total_counts} 条")
        if total_counts == 0:
            print("  [!] 没有发现新增离线数据，跳过提交。")
            return

        # 3. 提交同步
        print("\n3. [提交同步请求 /api/v2/sync/upload]...")
        sync_url = f"{BASE_URL}/api/v2/sync/upload"
        headers = {
            'Content-Type': 'application/json',
            'Authorization': AUTH_TOKEN
        }
        
        payload_json = json.dumps(payload)
        print("Payload Details:", json.dumps(payload["diseaseDetails"], indent=2, ensure_ascii=False))
        
        response = requests.post(sync_url, data=payload_json, headers=headers, timeout=60)
        
        print(f"\n状态码: {response.status_code}")
        if response.status_code == 200:
            result = response.json()
            print("响应结果:")
            print(json.dumps(result, indent=2, ensure_ascii=False))
            if result.get('code') in [0, 200]:
                print("\n[SUCCESS] 增量同步测试完成！后端已成功落库所有数据！")
            else:
                print("\n[FAILED] 服务端逻辑返回报错。")
        else:
            print(f"请求失败: {response.text}")

    finally:
        conn.close()

if __name__ == "__main__":
    main()
