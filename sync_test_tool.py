import sqlite3
import json
import uuid
import argparse
import requests
import os
import sys

# 配置区
BASE_URL = "http://localhost:80"  # 根据你的实际端口修改
SQLITE_FILE = "u95.db"
USER_ID = 95
CLIENT_INFO = "Python Automation Test Tool v1.1"
AUTH_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3NzU4MDc3MDQsInVzZXJuYW1lIjoiem5qY190ZXN0XzEifQ.qcttii7GbtjG-1lhUwuj2NJMcVWVOwv11LB5mUCp3hQ"

# --batch 模式下 uploadUuid 的 sidecar 持久化文件（与 SQLite 同目录），
# 模拟 App 端"uploadUuid 本地持久化、重试复用"；删除该文件相当于全部重传。
SIDECAR_FILE = "upload_uuid_sidecar.json"

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

# ==================== v1.1 批量幂等预上传（--batch） ====================

def ensure_upload_uuid_column(conn):
    """本地 bi_attachment 补齐 upload_uuid 列（模拟 App v1.4 表结构）"""
    try:
        conn.execute("ALTER TABLE bi_attachment ADD COLUMN upload_uuid TEXT")
    except sqlite3.OperationalError:
        pass  # 已存在

def load_sidecar():
    if os.path.exists(SIDECAR_FILE):
        try:
            with open(SIDECAR_FILE, encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            print(f"  [!] sidecar 读取失败，重新生成: {e}")
    return {}

def save_sidecar(data):
    with open(SIDECAR_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def attachment_key(att):
    """sidecar 键优先用附件 offlineUuid，退化到 id/name"""
    return att.get("offline_uuid") or f"rowid-{att.get('id')}" or f"name-{att.get('name')}"

def assign_upload_uuids(conn, attachments):
    """为每个附件落实持久化的 uploadUuid（已有则复用，模拟 App 重试复用/重拍换新）"""
    ensure_upload_uuid_column(conn)
    sidecar = load_sidecar()
    cur = conn.cursor()
    for att in attachments:
        key = attachment_key(att)
        up = att.get("upload_uuid") or sidecar.get(key)
        if not up:
            up = uuid.uuid4().hex
            sidecar[key] = up
            cur.execute(
                "UPDATE bi_attachment SET upload_uuid = ? WHERE id = ?",
                (up, att.get("id")))
        att["uploadUuid"] = up
    conn.commit()
    save_sidecar(sidecar)

def query_uploaded(uuids):
    """POST /sync/attachment/query，返回 {uploadUuid: status}"""
    url = f"{BASE_URL}/api/v2/sync/attachment/query"
    resp = requests.post(url, json={"uploadUuids": uuids},
                         headers={"Authorization": AUTH_TOKEN}, timeout=30)
    resp.raise_for_status()
    body = resp.json()
    if body.get("code") != 0:
        raise RuntimeError(f"回查失败: {body}")
    return {item["uploadUuid"]: item for item in (body.get("data") or [])}

def batch_upload_once(group):
    """一组文件走 /sync/attachment/batch；files 与 uploadUuids 各自按相同顺序对齐"""
    url = f"{BASE_URL}/api/v2/sync/attachment/batch"
    files = []
    data = []
    for att in group:
        name = att.get("name") or "test_offline.jpg"
        # dummy 内容不是合法图片：预期原图 success、缩略图 thumbMinioId=null（可作负样例）
        files.append(("files", (name, b"dummy_image_content", "image/jpeg")))
        data.append(("uploadUuids", att["uploadUuid"]))
    resp = requests.post(url, files=files, data=data,
                         headers={"Authorization": AUTH_TOKEN}, timeout=120)
    resp.raise_for_status()
    body = resp.json()
    if body.get("code") != 0:
        raise RuntimeError(f"批量预上传请求级失败: {body}")
    data_obj = body.get("data") or {}
    print(f"  [i] total={data_obj.get('total')} success={data_obj.get('successCount')} "
          f"exists={data_obj.get('existsCount')} failed={data_obj.get('failureCount')}")
    return data_obj.get("items") or []

def process_attachments_batch(conn, attachments, batch_size):
    """query 回查跳过已传 → 分批 batch 上传 → 双 id 回填；返回仍失败的附件列表"""
    assign_upload_uuids(conn, attachments)

    pending = [a for a in attachments if not a.get("minio_id") and not a.get("minioId")]
    if not pending:
        print("  [i] 无待传附件（本地均已有 minio_id）")
        return []

    # 1) 回查：单次 ≤500
    confirmed = {}
    all_uuids = [a["uploadUuid"] for a in pending]
    for i in range(0, len(all_uuids), 500):
        confirmed.update(query_uploaded(all_uuids[i:i + 500]))

    still = []
    hit = 0
    for att in pending:
        st = confirmed.get(att["uploadUuid"])
        if st and st.get("uploaded"):
            att["minioId"] = st["minioId"]
            att["thumbMinioId"] = st.get("thumbMinioId")
            hit += 1
        else:
            still.append(att)
    if hit:
        print(f"  [√] query 回查命中 {hit} 张，零流量跳过")

    # 2) 分批上传（批间串行；真实 App 可批间并发 ≤3）
    failed = []
    for i in range(0, len(still), batch_size):
        group = still[i:i + batch_size]
        print(f"  [+] 批量预上传 {len(group)} 张 (offset={i})")
        items = batch_upload_once(group)
        for att, item in zip(group, items):
            if item.get("status") == "failed":
                print(f"  [X] {att.get('name')} 失败: {item.get('error')}")
                failed.append(att)
                continue
            att["minioId"] = item["minioId"]
            att["thumbMinioId"] = item.get("thumbMinioId")
            print(f"  [√] {att.get('name')}: {item['status']} "
                  f"minioId={item['minioId']} thumb={item.get('thumbMinioId')}")
    return failed

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
    parser = argparse.ArgumentParser(description="通道A 离线同步模拟工具")
    parser.add_argument("--batch", action="store_true",
                        help="启用 v1.4 幂等批量模式：sidecar 持久化 uploadUuid → query 回查 → /sync/attachment/batch")
    parser.add_argument("--batch-size", type=int, default=8,
                        help="批量模式单批文件数（服务端硬上限 20，默认 8）")
    args = parser.parse_args()

    if not os.path.exists(SQLITE_FILE):
        print(f"错误: 找不到 {SQLITE_FILE} 文件，请确保它在当前目录。")
        sys.exit(1)

    mode = "批量幂等(batch+query)" if args.batch else "旧版串行(裸 minioId)"
    print(f"=== 启动离线同步自动化测试 (Target: {BASE_URL}, 模式: {mode}) ===")

    conn = get_db_connection()
    try:
        # 0. 构造模拟数据（模拟移动端新增）
        print("\n0. [构造模拟离线数据]...")
        mock_offline_data(conn)

        # 1. 抽取附件数据并模拟上传
        print("\n1. [处理附件上传]...")
        # 注意：这里我们只模拟上传属于离线记录的附件
        attachments = fetch_table_data(conn, "bi_attachment")
        failed = []
        if args.batch:
            failed = process_attachments_batch(conn, attachments, args.batch_size)
        else:
            for attr in attachments:
                server_id = upload_attachment(attr.get('name') or "test_offline.jpg")
                if server_id:
                    attr['minioId'] = server_id
                else:
                    failed.append(attr)

        if failed:
            print(f"\n[!] {len(failed)} 个附件预上传失败，仍继续提交（服务端将逐行报错），"
                  f"批量模式下重跑本脚本即可凭 uploadUuid 跳过已传项。")
        
        # 2. 提取业务数据 (仅限 is_offline_data = 1)
        print("\n2. [提取增量业务数据]...")
        payload = {
            "syncUuid": str(uuid.uuid4()),
            "userId": USER_ID,
            "clientInfo": CLIENT_INFO,
            "buildings": fetch_table_data(conn, "bi_building"),
            "objects": fetch_table_data(conn, "bi_object"),
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
