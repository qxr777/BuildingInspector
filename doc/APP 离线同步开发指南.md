# App 离线同步开发指南

> 文档版本：v1.1  
> 最后更新：2026-04-18  
> 适用对象：App 端开发人员 (Vue.js + Capacitor/Cordova)

**技术栈说明：** 本 App 采用 Vue.js 框架开发，使用 Capacitor/Cordova 访问原生 SQLite 能力。

---

## 目录

1. [概述](#1-概述)
2. [同步流程](#2-同步流程)
3. [API 接口](#3-api-接口)
4. [数据格式规范](#4-数据格式规范)
5. [SQLite 表结构要求](#5-sqlite-表结构要求)
6. [字段映射规则](#6-字段映射规则)
7. [离线删除机制](#7-离线删除机制)
8. [错误处理](#8-错误处理)
9. [最佳实践](#9-最佳实践)
10. [常见问题](#10-常见问题)

---

## 1. 概述

### 1.1 同步机制说明

本系统采用 **离线优先 (Offline-First)** 架构，App 端在离线环境下采集的数据暂存本地 SQLite，网络恢复后通过同步接口上传至服务端。

**核心设计：**
- 每条离线记录生成唯一 `offlineUuid` (UUID-4)
- 服务端通过 `offlineUuid` 判断是新增还是更新
- 通过 `offlineDeleted` 字段标记删除操作
- 通过 `is_offline_data` 标识数据来源于离线端

### 1.2 支持同步的实体

| 实体 | 表名 | 说明 |
|------|------|------|
| Building | `bi_building` | 建筑物/桥梁 |
| BiObject | `bi_object` | 结构物 (树形结构) |
| Component | `bi_component` | 构件 |
| Disease | `bi_disease` | 病害 |
| DiseaseDetail | `bi_disease_detail` | 病害详情 |
| Attachment | `bi_attachment` | 附件 (图片等) |
| BiObjectComponent | `bi_object_component` | 构件 - 对象关联 (2026 新标) |

---

## 2. 同步流程

### 2.1 核心设计理念：Post-Submit Rebuild & Fetch

本系统采用 **"提交后立即重建 & 获取"** 模型，极大简化 App 端逻辑：

```
传统双向同步：App 提交 → 查询变更 → 本地合并 → 解决冲突 → 完成
                                                             ↑
                                                        复杂逻辑

本系统方案：    App 提交 → 服务端合并 → 返回全新 SQLite → App 覆盖本地
                                                             ↑
                                                      简单覆盖
```

**核心优势：**
- **零合并逻辑**：App 端无需实现复杂的数据合并算法
- **强一致性**：下载的 SQLite 包含云端所有最新数据（包括其他设备的变更）
- **简化流程**：一次请求完成"上传 + 下载"闭环

### 2.2 完整同步时序

```
┌─────────┐         ┌─────────┐         ┌─────────┐
│  App 端   │         │  MinIO  │         │  服务端  │
└────┬────┘         └────┬────┘         └────┬────┘
     │                   │                   │
     │ 1. 循环上传附件    │                   │
     │──────────────────>│                   │
     │                   │                   │
     │ 2. 返回 serverId   │                   │
     │<──────────────────│                   │
     │                   │                   │
     │ 3. 构造 JSON 载荷   │                   │
     │                   │                   │
     │ 4. POST /sync/upload                  │
     │──────────────────────────────────────>│
     │                   │                   │
     │ 5. 处理并落库      │                   │
     │ 6. 重建用户 SQLite │                   │
     │ 7. 上传至 MinIO    │                   │
     │                   │                   │
     │ 8. 返回下载地址    │                   │
     │<──────────────────────────────────────│
     │   { url, timestamp, size }            │
     │                   │                   │
     │ 9. 后台下载新.db   │                   │
     │──────────────────>│                   │
     │                   │                   │
     │ 10. 替换本地文件   │                   │
     │                   │                   │
```

### 2.3 步骤说明

**步骤 1：附件预上传**

在提交同步前，先遍历所有 `is_offline_data = 1` 的附件记录，逐个上传到 MinIO：

```
POST /api/v2/sync/attachment
Content-Type: multipart/form-data

file: [图片二进制]
```

响应：
```json
{
  "code": 0,
  "data": 12345  // serverId，即 minioId
}
```

**步骤 2：更新本地附件记录**

将返回的 `serverId` 写入本地 `bi_attachment.minio_id` 字段。

**步骤 3：构造同步载荷**

提取所有 `is_offline_data = 1` 且未同步的业务数据，构造 JSON（详见第 4 节）。

**步骤 4：提交同步**

```
POST /api/v2/sync/upload
Authorization: Bearer {token}
Content-Type: application/json

{ ...payload... }
```

**步骤 5-7：服务端处理（自动）**

服务端自动完成：
1. 解析 JSON 并落库
2. 合并 ID 映射
3. **重建用户专属 SQLite**（包含所有最新数据）
4. 上传至 MinIO

**步骤 8：返回下载地址**

响应中包含完整 SQLite 的下载 URL。

**步骤 9-10：App 下载并替换**

App 在后台下载新 `.db` 文件，替换本地旧数据库。

```
POST /api/v2/sync/upload
Content-Type: application/json
Authorization: Bearer {token}

{ ...payload... }
```

**步骤 5-7：处理响应**

- 成功：更新本地记录的 `is_offline_data = 0`，保存 ID 映射
- 失败：保留离线标记，记录错误日志，等待重试

---

## 3. API 接口

### 3.0 认证说明 (重要)

所有 API 请求需要在 Header 中携带 JWT Token：

```http
Authorization: Bearer {token}
```

#### 如何获取 Token

**登录接口：**

| 属性 | 值 |
|------|-----|
| 路径 | `/jwt/login` |
| 方法 | POST |
| 类型 | `application/x-www-form-urlencoded` |

**请求：**
```http
POST /jwt/login
Content-Type: application/x-www-form-urlencoded

username=admin&password=yourPassword
```

**响应 (成功)：**
```json
{
  "code": 0,
  "msg": "登录成功，请妥善保管您的 token 信息",
  "token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3NzU4MDc3MDQsInVzZXJuYW1lIjoiYWRtaW4ifQ.xxx",
  "userId": 1,
  "userName": "管理员",
  "userDept": "信息中心"
}
```

**Token 说明：**
- 有效期：**60 分钟** (30 * 60 * 1000 * 2 毫秒)
- 加密方式：HMAC256，使用用户密码作为密钥
- 过期后需重新登录获取新 Token

**Vue App 存储建议：**

```javascript
// 使用 localStorage 存储 Token
// store/modules/auth.js
export default {
  state: {
    token: localStorage.getItem('jwt_token') || '',
    userId: localStorage.getItem('user_id') || '',
  },
  
  mutations: {
    SET_TOKEN(state, { token, userId, userName }) {
      state.token = token
      state.userId = userId
      localStorage.setItem('jwt_token', token)
      localStorage.setItem('user_id', userId)
      localStorage.setItem('user_name', userName)
    },
    
    CLEAR_TOKEN(state) {
      state.token = ''
      state.userId = ''
      localStorage.removeItem('jwt_token')
      localStorage.removeItem('user_id')
      localStorage.removeItem('user_name')
    }
  },
  
  actions: {
    async login({ commit }, { username, password }) {
      const response = await axios.post('/jwt/login', {
        username,
        password
      })
      if (response.data.code === 0) {
        commit('SET_TOKEN', response.data)
      }
      return response
    }
  }
}
```

**Axios 拦截器自动添加 Token：**

```javascript
// utils/request.js
import axios from 'axios'
import store from '@/store'

const service = axios.create({
  baseURL: process.env.VUE_APP_BASE_API || 'http://localhost:80',
  timeout: 60000
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    const token = store.state.token
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器 - 处理 401
service.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      store.commit('CLEAR_TOKEN')
      router.replace('/login')
    }
    return Promise.reject(error)
  }
)

export default service
```

---

### 3.1 附件预上传

| 属性 | 值 |
|------|-----|
| 路径 | `/api/v2/sync/attachment` |
| 方法 | POST |
| 认证 | 需要 (JWT Token) |
| 类型 | `multipart/form-data` |

**请求：**
```http
POST /api/v2/sync/attachment
Authorization: eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...

file: [binary]
```

**响应：**
```json
{
  "code": 0,
  "msg": "上传成功",
  "data": 288  // 文件记录 ID
}
```

### 3.2 数据同步提交

| 属性 | 值 |
|------|-----|
| 路径 | `/api/v2/sync/upload` |
| 方法 | POST |
| 认证 | 需要 (JWT Token) |
| 类型 | `application/json` |
| 超时建议 | ≥ 60 秒 |

**请求：**
```http
POST /api/v2/sync/upload
Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...
Content-Type: application/json
Timeout: 60000

{
  "syncUuid": "550e8400-e29b-41d4-a716-446655440000",  // 可选，服务端会生成
  "clientInfo": "BridgeInspector-iOS/2.1.0",          // 可选
  "buildings": [...],
  "objects": [...],
  "components": [...],
  "diseases": [...],
  "diseaseDetails": [...],
  "attachments": [...],
  "biObjectComponents": [...]  // 2026 新标，可选
}
```

**响应 (成功 - 含 SQLite 下载地址)：**

```json
{
  "code": 0,
  "msg": "同步完成",
  "data": {
    "url": "http://59.110.81.142:9000/public/ab/ab1234567890abcdef.db",
    "timestamp": 1713427200000,
    "size": "2.5 MB"
  }
}
```

**响应字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `url` | string | 完整 SQLite 文件下载地址 (MinIO) |
| `timestamp` | number | 生成时间戳 (毫秒) |
| `size` | string | 文件大小 (人类可读格式) |

**Vue App 端处理流程：**

```vue
<!-- views/sync/SyncManager.vue -->
<template>
  <div>
    <van-button type="primary" @click="startSync" :loading="syncing">
      {{ syncing ? '同步中...' : '开始同步' }}
    </van-button>
    <van-progress :percentage="progress" v-if="syncing" />
  </div>
</template>

<script>
import axios from '@/utils/request'
import { openDB } from '@capacitor-community/sqlite'
import { Filesystem, Directory } from '@capacitor/filesystem'

export default {
  name: 'SyncManager',
  data() {
    return {
      syncing: false,
      progress: 0
    }
  },
  methods: {
    async startSync() {
      this.syncing = true
      this.progress = 0
      
      try {
        // 1. 上传附件
        await this.uploadAttachments()
        this.progress = 20
        
        // 2. 提交同步数据
        const payload = await this.buildSyncPayload()
        const response = await axios.post('/api/v2/sync/upload', payload)
        this.progress = 80
        
        if (response.data.code === 0) {
          // 3. 下载并替换 SQLite
          const dbUrl = response.data.data.url
          await this.downloadAndReplaceDatabase(dbUrl)
          this.progress = 100
          this.$toast.success('同步完成')
        }
      } catch (error) {
        this.$toast.fail('同步失败：' + error.message)
      } finally {
        this.syncing = false
      }
    },
    
    async uploadAttachments() {
      const db = await openDB('BridgeInspector', 1, true, false)
      const result = await db.query('SELECT * FROM bi_attachment WHERE is_offline_data = 1')
      
      for (const att of (result.values || [])) {
        const fileData = await this.getLocalFile(att.local_path)
        const formData = new FormData()
        formData.append('file', fileData)
        
        const res = await axios.post('/api/v2/sync/attachment', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        
        if (res.data.code === 0) {
          await db.run('UPDATE bi_attachment SET minio_id = ? WHERE id = ?', 
            [res.data.data, att.id])
        }
      }
    },
    
    async buildSyncPayload() {
      const db = await openDB('BridgeInspector', 1, true, false)
      
      const buildTableData = async (table) => {
        const res = await db.query(`SELECT * FROM ${table} WHERE is_offline_data = 1`)
        return (res.values || []).map(row => ({
          ...row,
          offlineUuid: row.offline_uuid,
          isOfflineData: row.is_offline_data,
          offlineDeleted: row.offline_deleted
        }))
      }
      
      return {
        syncUuid: this.generateUuid(),
        clientInfo: 'BridgeInspector-Vue/2.1.0',
        buildings: await buildTableData('bi_building'),
        objects: await buildTableData('bi_object'),
        components: await buildTableData('bi_component'),
        diseases: await buildTableData('bi_disease'),
        diseaseDetails: await buildTableData('bi_disease_detail'),
        attachments: await buildTableData('bi_attachment')
      }
    },
    
    async downloadAndReplaceDatabase(url) {
      const dbPath = 'BridgeInspector.db'
      const tempPath = 'temp_sync.db'
      
      try {
        // 1. 下载数据库文件
        await Filesystem.downloadFile({
          url,
          path: tempPath,
          directory: Directory.Data
        })
        
        // 2. 关闭当前数据库连接
        // 注意：需要确保所有正在使用的地方都已关闭
        const db = await openDB('BridgeInspector', 1, false, false)
        await db.close()
        
        // 3. 删除旧数据库文件（如果存在）
        try {
          await Filesystem.delete({ path: dbPath, directory: Directory.Data })
        } catch (e) {
          // 文件不存在，忽略
        }
        
        // 4. 重命名临时文件为正式数据库文件
        await Filesystem.rename({
          from: tempPath,
          to: dbPath,
          directory: Directory.Data
        })
        
        // 5. 重新打开数据库
        await openDB('BridgeInspector', 1, false, false)
        
        // 6. 通知数据库已更新
        this.$emit('database-updated')
        
        return true
      } catch (error) {
        console.error('数据库替换失败:', error)
        throw error
      }
    },
    
    getLocalFile(localPath) {
      // 使用 Capacitor Filesystem 读取本地文件
      return new Promise((resolve) => {
        Filesystem.readFile({ path: localPath, directory: Directory.Data })
          .then(result => {
            const blob = this.base64ToBlob(result.data, 'image/jpeg')
            resolve(new File([blob], 'image.jpg', { type: 'image/jpeg' }))
          })
      })
    },
    
    base64ToBlob(base64, mimeType) {
      const byteCharacters = atob(base64)
      const byteNumbers = new Array(byteCharacters.length)
      for (let i = 0; i < byteCharacters.length; i++) {
        byteNumbers[i] = byteCharacters.charCodeAt(i)
      }
      const byteArray = new Uint8Array(byteNumbers)
      return new Blob([byteArray], { type: mimeType })
    },
    
    generateUuid() {
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
        const r = Math.random() * 16 | 0
        return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
      })
    }
  }
}
</script>
```

**响应 (失败)：**
```json
{
  "code": 500,
  "msg": "离线数据上传并同步处理异常：建筑物 UUID 重复",
  "data": null
}
```

**错误码说明：**

| code | 说明 | 处理建议 |
|------|------|---------|
| 0 | 同步成功 | 更新本地 `is_offline_data=0`，保存 `serverId` |
| 200 | 同步成功 | 同上 |
| 401 | 认证失败 | Token 过期或无效，需重新登录 |
| 403 | 权限不足 | 检查用户权限 |
| 500 | 服务端错误 | 查看 `msg` 详情，记录日志后重试 |

**注意事项：**

1. **请求大小限制**：建议单次请求 ≤ 5MB，超大数据请分批
2. **超时设置**：建议 ≥ 60 秒，复杂数据可能需要更长时间
3. **幂等性**：同一 `syncUuid` 重复提交会覆盖已有数据
4. **事务性**：所有实体要么全部成功，要么全部回滚

### 3.3 获取用户 SQLite (可选)

同步成功后，可调用此接口获取重新打包的完整 SQLite：

| 属性 | 值 |
|------|-----|
| 路径 | `/api/v2/user/{id}/sqlite` |
| 方法 | GET |
| 认证 | 需要 (JWT Token) |

**请求示例：**

```http
GET /api/v2/user/1/sqlite
Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...
```

**响应：**

```json
{
  "code": 0,
  "msg": "获取成功",
  "data": {
    "url": "http://59.110.81.142:9000/public/ab/ab1234567890abcdef.db",
    "timestamp": 1713427200000,
    "size": "2.5 MB"
  }
}
```

**Vue.js 调用示例：**

```javascript
import axios from '@/utils/request'
import { useDatabaseSync } from '@/composables/useDatabaseSync'

const { downloadDatabase } = useDatabaseSync()

const fetchLatestDatabase = async (userId) => {
  const response = await axios.get(`/api/v2/user/${userId}/sqlite`)
  if (response.data.code === 0) {
    const dbUrl = response.data.data.url
    await downloadDatabase(dbUrl)
  }
}
```

**说明：** 此接口通常不需要主动调用，因为 `/api/v2/sync/upload` 已经返回了 SQLite 下载地址。仅在需要单独获取最新数据库时使用。

---

## 4. 数据格式规范

### 4.1 同步载荷结构

```typescript
interface SyncPayload {
  syncUuid?: string;      // 可选，服务端会生成
  clientInfo?: string;    // 可选，客户端标识
  buildings: Building[];
  objects: BiObject[];
  components: Component[];
  diseases: Disease[];
  diseaseDetails: DiseaseDetail[];
  attachments: Attachment[];
  biObjectComponents?: BiObjectComponent[]; // 2026 新标
}
```

### 4.2 各实体字段要求

#### Building

```typescript
interface Building {
  offlineUuid: string;        // 必需，UUID-4
  offlineDeleted?: number;    // 0=新增/更新，1=删除
  name: string;
  area: string;
  line: string;
  status: string;
  isLeaf: string;
  rootObjectUuid?: string;    // 关联对象的 UUID
  isOfflineData?: number;     // 只读，服务端填充
}
```

#### BiObject

```typescript
interface BiObject {
  offlineUuid: string;        // 必需
  offlineDeleted?: number;
  parentUuid?: string;        // 父节点 UUID (根节点为 0 或空)
  buildingUuid?: string;      // 所属建筑物 UUID
  name: string;
  ancestors?: string;         // 可选，服务端会计算
  status: string;
  templateObjectId?: number;
  isOfflineData?: number;
}
```

#### Component

```typescript
interface Component {
  offlineUuid: string;        // 必需
  offlineDeleted?: number;
  objectUuid?: string;        // 所属对象 UUID
  name: string;
  code: string;
  status: string;
  edi?: number;               // 评定标度
  efi?: number;
  eai?: number;
  isOfflineData?: number;
}
```

#### Disease

```typescript
interface Disease {
  offlineUuid: string;        // 必需
  offlineDeleted?: number;
  buildingUuid?: string;
  objectUuid?: string;
  componentUuid?: string;
  description: string;
  type: string;
  level: number;
  quantity: number;
  commitType?: number;        // 1=离线采集
  taskId?: number;
  images?: string[];          // 图片文件名列表
  ADImgs?: string[];          // AD 图片列表
  isOfflineData?: number;
}
```

#### DiseaseDetail

```typescript
interface DiseaseDetail {
  offlineUuid?: string;       // 可选
  offlineDeleted?: number;
  diseaseUuid: string;        // 关联病害 UUID
  reference1Location?: string;
  reference1LocationStart?: number;
  reference1LocationEnd?: number;
  width?: number;
  length1?: number;
  // ... 其他测量字段
}
```

#### Attachment

```typescript
interface Attachment {
  offlineUuid?: string;
  offlineDeleted?: number;
  offlineSubjectUuid?: string;  // 关联主体 UUID
  name: string;                 // 文件名
  type: number;                 // 附件类型（见下表）
  minioId?: number;             // 预上传后填充
  thumbMinioId?: number;        // 缩略图 ID
}
```

**Attachment.type 取值说明：**

| type 值 | 说明 | 使用场景 |
|---------|------|---------|
| `null` 或 `0` | 病害附件（默认） | 病害图片 |
| `1` | 普通病害图片 | 常规病害采集图片 |
| `2` | 设备附件 | 检测设备相关文件 |
| `5` | 标准文档 | 规范、标准文档 |
| `6` | 正立面照片 | 桥梁正立面全景照片 |
| `7` | 病害 AD 图片 | 病害 AD 识别相关图片 |
| `8` | 部件当前照片 | 部件现状照片 |

**建议：** App 端采集病害图片时，推荐使用 `type: 1`（普通病害图片）。

---

## 5. SQLite 表结构要求

### 5.1 通用字段

所有参与同步的表必须包含以下字段：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `offline_uuid` | TEXT | 离线 UUID (主键或唯一索引) |
| `is_offline_data` | INTEGER | 0=云端数据，1=离线生成 |
| `offline_deleted` | INTEGER | 0=正常，1=已删除 |

### 5.2 各表外键 UUID 字段

| 表名 | UUID 字段 | 关联目标 |
|------|---------|---------|
| `bi_building` | `root_object_uuid` | → bi_object.offline_uuid |
| `bi_object` | `parent_uuid` | → bi_object.offline_uuid (自引用) |
| `bi_object` | `building_uuid` | → bi_building.offline_uuid |
| `bi_component` | `object_uuid` | → bi_object.offline_uuid |
| `bi_disease` | `building_uuid` | → bi_building.offline_uuid |
| `bi_disease` | `object_uuid` | → bi_object.offline_uuid |
| `bi_disease` | `component_uuid` | → bi_component.offline_uuid |
| `bi_disease` | `offline_uuid` | 本记录 UUID |
| `bi_disease_detail` | `disease_uuid` | → bi_disease.offline_uuid |
| `bi_attachment` | `offline_subject_uuid` | → 对应主体的 offline_uuid |
| `bi_object_component` | `object_uuid` | → bi_object.offline_uuid |
| `bi_object_component` | `component_uuid` | → bi_component.offline_uuid |

### 5.3 DDL 示例

```sql
-- Building 表
CREATE TABLE bi_building (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    offline_uuid TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    area TEXT,
    line TEXT,
    status TEXT DEFAULT '0',
    is_leaf TEXT DEFAULT '1',
    root_object_uuid TEXT,
    is_offline_data INTEGER DEFAULT 0,
    offline_deleted INTEGER DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME
);

-- BiObject 表
CREATE TABLE bi_object (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    offline_uuid TEXT NOT NULL UNIQUE,
    parent_uuid TEXT,
    building_uuid TEXT,
    name TEXT NOT NULL,
    ancestors TEXT,
    status TEXT DEFAULT '0',
    template_object_id INTEGER,
    is_offline_data INTEGER DEFAULT 0,
    offline_deleted INTEGER DEFAULT 0,
    FOREIGN KEY (parent_uuid) REFERENCES bi_object(offline_uuid),
    FOREIGN KEY (building_uuid) REFERENCES bi_building(offline_uuid)
);

-- Disease 表
CREATE TABLE bi_disease (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    offline_uuid TEXT NOT NULL UNIQUE,
    building_uuid TEXT,
    object_uuid TEXT,
    component_uuid TEXT,
    description TEXT,
    type TEXT,
    level INTEGER,
    quantity REAL,
    commit_type INTEGER DEFAULT 1,
    is_offline_data INTEGER DEFAULT 0,
    offline_deleted INTEGER DEFAULT 0
);
```

---

## 6. 字段映射规则

### 6.1 命名转换

SQLite 使用下划线命名，App 端需转换为驼峰：

| SQLite 字段 | App/JSON 字段 |
|------------|--------------|
| `offline_uuid` | `offlineUuid` |
| `is_offline_data` | `isOfflineData` |
| `offline_deleted` | `offlineDeleted` |
| `parent_uuid` | `parentUuid` |
| `building_uuid` | `buildingUuid` |
| `object_uuid` | `objectUuid` |
| `component_uuid` | `componentUuid` |
| `disease_uuid` | `diseaseUuid` |
| `offline_subject_uuid` | `offlineSubjectUuid` |

### 6.2 类型转换

| SQLite | JSON/TypeScript | 说明 |
|--------|----------------|------|
| INTEGER | number | |
| TEXT | string | |
| REAL | number | |
| DATETIME | string (ISO8601) | `YYYY-MM-DD HH:mm:ss` |
| NULL | null | |

---

## 7. 离线删除机制

### 7.1 删除流程

当用户在 App 端删除一条记录时：

1. **不要物理删除**本地记录
2. 设置 `offline_deleted = 1`
3. 设置 `is_offline_data = 1` (如果原来是 0，表示云端数据被离线删除)
4. 下次同步时，服务端会执行对应的软删除

### 7.2 代码示例 (Vue.js)

```javascript
// stores/sync.js - Pinia Store
import { defineStore } from 'pinia'
import { openDB } from '@capacitor-community/sqlite'

export const useSyncStore = defineStore('sync', {
  state: () => ({
    isSyncing: false
  }),
  
  actions: {
    async markAsDeleted(table, id) {
      const db = await openDB('BridgeInspector', 1, true, false)
      await db.run(
        `UPDATE ${table} SET offline_deleted = 1, is_offline_data = 1 WHERE id = ?`,
        [id]
      )
      // 标记为待同步
      await this.addSyncQueue(table, id)
    },
    
    async addSyncQueue(table, id) {
      // 添加到同步队列（可使用 localStorage 持久化）
      const queue = JSON.parse(localStorage.getItem('sync_queue') || '[]')
      queue.push({ table, id, timestamp: Date.now() })
      localStorage.setItem('sync_queue', JSON.stringify(queue))
    }
  }
})

// 使用示例
// components/BuildingList.vue
<script setup>
import { useSyncStore } from '@/stores/sync'

const syncStore = useSyncStore()

const deleteBuilding = async (building) => {
  await syncStore.markAsDeleted('bi_building', building.id)
  // 从本地列表移除（UI 层面）
  buildings.value = buildings.value.filter(b => b.id !== building.id)
}
</script>
```

### 7.3 注意事项

| 实体 | 删除处理说明 |
|------|-------------|
| Building | 级联删除所有子对象、构件、病害 |
| BiObject | 级联删除子对象、构件、病害 |
| Component | 检查是否有关联病害 |
| Disease | 级联删除 DiseaseDetail |
| Attachment | **当前版本暂不支持删除** |

---

## 8. 错误处理

### 8.1 常见错误码

| code | 说明 | 处理建议 |
|------|------|---------|
| 0 | 成功 | 更新本地状态 |
| 200 | 成功 | 同上 |
| 401 | 认证失败 | 刷新 Token 后重试 |
| 500 | 服务端错误 | 记录日志，等待人工介入 |

### 8.2 重试策略

```typescript
const RETRY_CONFIG = {
  maxRetries: 3,
  initialDelay: 1000,      // 1 秒
  maxDelay: 30000,         // 30 秒
  backoffMultiplier: 2,    // 指数退避
};

async function syncWithRetry(payload: SyncPayload): Promise<SyncResult> {
  let delay = RETRY_CONFIG.initialDelay;
  
  for (let i = 0; i < RETRY_CONFIG.maxRetries; i++) {
    try {
      return await submitSync(payload);
    } catch (error) {
      if (error.status === 401) {
        await refreshAuth();
        continue;
      }
      if (i === RETRY_CONFIG.maxRetries - 1) throw error;
      await sleep(delay);
      delay *= RETRY_CONFIG.backoffMultiplier;
    }
  }
}
```

### 8.3 部分失败处理

如果响应中包含 `idMappings` 但 `failureCount > 0`：

1. 成功的记录：更新本地 `is_offline_data = 0`，保存 `serverId`
2. 失败的记录：查看 `errors` 数组，记录错误原因
3. 用户可手动重试失败记录

---

## 9. 最佳实践

### 9.1 UUID 生成 (Vue.js)

```javascript
// utils/uuid.js
export function generateUuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
  })
}

// 或使用 crypto API（更安全）
export function generateSecureUuid() {
  return ([1e7] + -1e3 + -4e3 + -8e3 + -1e11).replace(/[018]/g, c =>
    (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16)
  )
}

// 使用
import { generateUuid } from '@/utils/uuid'
const uuid = generateUuid()
```

### 9.2 同步粒度

- 建议每 **50-100 条记录** 分批同步
- 单次请求 JSON 大小建议不超过 **5MB**
- 附件上传建议并发数 ≤ 3

### 9.3 本地状态管理

建议增加本地状态字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sync_status` | TEXT | `pending` / `synced` / `failed` |
| `sync_error` | TEXT | 失败原因 |
| `sync_time` | DATETIME | 最后同步时间 |
| `server_id` | INTEGER | 服务端 ID (同步后填充) |

### 9.4 网络优化

- 使用 **WiFi 优先** 策略
- 图片上传前进行 **压缩** (建议 ≤ 500KB)
- 弱网环境下降低并发数

### 9.5 日志记录

建议记录以下日志用于排查：

```
[Sync] 开始同步，UUID: xxx, 记录数：42
[Sync] 附件上传：0_front_1.jpg → serverId: 288
[Sync] 提交载荷，大小：1.2MB
[Sync] 服务端响应：success=42, failed=0
[Sync] 开始下载新数据库...
[Sync] 数据库替换完成
```

### 9.6 数据库下载策略 (重要)

**同步成功后，App 应立即在后台下载新的 SQLite：**

```javascript
// composables/useDatabaseSync.js
import { ref } from 'vue'
import { Filesystem, Directory } from '@capacitor/filesystem'
import { openDB } from '@capacitor-community/sqlite'

export function useDatabaseSync() {
  const downloading = ref(false)
  const progress = ref(0)
  
  const downloadDatabase = async (url) => {
    downloading.value = true
    progress.value = 0
    
    try {
      const dbPath = 'BridgeInspector.db'
      const tempPath = 'temp_sync.db'
      
      // 1. 下载数据库文件
      await Filesystem.downloadFile({
        url,
        path: tempPath,
        directory: Directory.Data
      })
      
      progress.value = 50
      
      // 2. 关闭当前数据库连接
      // 注意：确保所有正在使用的地方都已关闭
      const db = await openDB('BridgeInspector', 1, false, false)
      await db.close()
      
      // 3. 删除旧数据库文件（如果存在）
      try {
        await Filesystem.delete({ path: dbPath, directory: Directory.Data })
      } catch (e) {
        // 文件不存在，忽略
      }
      
      // 4. 重命名临时文件为正式数据库文件（原子操作）
      await Filesystem.rename({
        from: tempPath,
        to: dbPath,
        directory: Directory.Data
      })
      
      progress.value = 100
      
      // 5. 重新打开数据库
      await openDB('BridgeInspector', 1, false, false)
      
      return true
    } catch (error) {
      console.error('数据库下载失败:', error)
      throw error
    } finally {
      downloading.value = false
    }
  }
  
  return {
    downloading,
    progress,
    downloadDatabase
  }
}
```

```vue
<!-- 使用示例 -->
<script setup>
import { useDatabaseSync } from '@/composables/useDatabaseSync'

const { downloading, progress, downloadDatabase } = useDatabaseSync()

const handleSyncResponse = async (response) => {
  const dbUrl = response.data.data.url
  const success = await downloadDatabase(dbUrl)
  
  if (success) {
    // 刷新页面数据
    location.reload()
  }
}
</script>
```

**注意事项：**

| 项目 | 建议 |
|------|------|
| 下载时机 | 同步成功后立即下载，或推迟到 WiFi 环境 |
| 数据库锁 | 下载完成前确保无活跃数据库连接 |
| 原子替换 | 使用 `rename` 而非 复制 + 删除 |
| 回滚方案 | 保留旧数据库副本直到新库验证通过 |

---

## 10. 常见问题
**注意事项：**

| 项目 | 建议 |
|------|------|
| 下载时机 | 同步成功后立即下载，或推迟到 WiFi 环境 |
| 数据库锁 | 下载完成前确保无活跃数据库连接 |
| 原子替换 | 使用 `rename` 而非 复制+删除 |
| 回滚方案 | 保留旧数据库副本直到新库验证通过 |

---

## 10. 常见问题

### Q1: 同步时提示"UUID 重复"

**原因：** 两条本地记录生成了相同的 UUID，或重复提交了已同步的记录。

**解决：**
- 检查 UUID 生成逻辑
- 同步成功后立即标记 `is_offline_data = 0`

### Q2: BiObject 同步后父子关系丢失

**原因：** `parentUuid` 指向的记录还未同步。

**解决：** 确保 Building 先于 Object 同步（已在协议中保证顺序）。

### Q3: 图片上传成功但同步失败

**原因：** 附件预上传与数据同步是分离的。

**解决：** 记录已上传的 `minioId`，下次同步时复用；或实现清理接口。

### Q4: 如何查看同步日志？

服务端可通过 `bi_sync_log` 表查询：

```sql
SELECT * FROM bi_sync_log 
WHERE user_id = ? 
ORDER BY create_time DESC 
LIMIT 10;
```

### Q5: 多设备同时登录怎么办？

当前版本 **不支持** 多设备并发同步同一用户数据。建议：
- App 端检测账号切换，清空本地缓存
- 或实现设备锁机制

### Q6: Token 过期了怎么办？

**方案一：主动刷新**

Token 有效期为 60 分钟，建议在检测到剩余时间 < 5 分钟时主动刷新：

```javascript
// utils/auth.js
import { jwtDecode } from 'jwt-decode'

function isTokenExpiringSoon(token, threshold = 300) {
  const { exp } = jwtDecode(token)
  const now = Date.now() / 1000
  return exp - now < threshold
}

export async function refreshTokenIfNeeded() {
  const token = localStorage.getItem('jwt_token')
  if (token && isTokenExpiringSoon(token, 300)) {
    await store.dispatch('auth/refreshToken')
  }
}
```

**方案二：被动刷新**

捕获 401 错误，自动重试登录（已在 axios 拦截器中实现，见第 3 节）。

### Q7: 如何安全存储 Token？

| 平台 | 推荐方案 |
|------|---------|
| Vue.js Web/H5 | localStorage (HTTPS only) + 内存 |
| Capacitor/Cordova | @capacitor/storage (加密插件) |
| 鸿蒙 | Preferences (加密) |

**建议实践：**

```javascript
// 使用加密存储（Capacitor）
import { Storage } from '@capacitor/storage'

export const authStorage = {
  async setToken(token) {
    await Storage.set({ key: 'jwt_token', value: token })
  },
  async getToken() {
    const { value } = await Storage.get({ key: 'jwt_token' })
    return value
  },
  async removeToken() {
    await Storage.remove({ key: 'jwt_token' })
  }
}
```

**不要** 将 Token 存储在：
- localStorage/sessionStorage (未加密，易受 XSS 攻击)
- SQLite 数据库 (明文)
- 日志文件中

---

## 附录 A: 完整同步示例

### 请求示例

```json
{
  "syncUuid": "550e8400-e29b-41d4-a716-446655440000",
  "clientInfo": "BridgeInspector-Vue/2.1.0",
  "buildings": [
    {
      "offlineUuid": "b1-uuid-xxx",
      "name": "学海桥",
      "area": "武汉市",
      "line": "二环线",
      "status": "0",
      "isLeaf": "1",
      "offlineDeleted": 0
    }
  ],
  "objects": [
    {
      "offlineUuid": "o1-uuid-xxx",
      "buildingUuid": "b1-uuid-xxx",
      "parentUuid": "0",
      "name": "第一跨",
      "status": "0",
      "offlineDeleted": 0
    }
  ],
  "components": [
    {
      "offlineUuid": "c1-uuid-xxx",
      "objectUuid": "o1-uuid-xxx",
      "name": "左侧梁",
      "code": "L01",
      "status": "0",
      "offlineDeleted": 0
    }
  ],
  "diseases": [
    {
      "offlineUuid": "d1-uuid-xxx",
      "buildingUuid": "b1-uuid-xxx",
      "objectUuid": "o1-uuid-xxx",
      "componentUuid": "c1-uuid-xxx",
      "description": "梁体裂缝",
      "type": "1",
      "level": 2,
      "quantity": 3,
      "commitType": 1,
      "offlineDeleted": 0
    }
  ],
  "diseaseDetails": [
    {
      "diseaseUuid": "d1-uuid-xxx",
      "reference1Location": "跨中",
      "width": 0.3,
      "length1": 150,
      "offlineDeleted": 0
    }
  ],
  "attachments": [
    {
      "offlineUuid": "a1-uuid-xxx",
      "offlineSubjectUuid": "d1-uuid-xxx",
      "name": "0_front_crack.jpg",
      "type": 1,
      "minioId": 288,
      "offlineDeleted": 0
    }
  ]
}
```

### 响应示例 (同步成功 + SQLite 下载地址)

```json
{
  "code": 0,
  "msg": "同步完成",
  "data": {
    "url": "http://59.110.81.142:9000/public/ab/ab1234567890abcdef.db",
    "timestamp": 1713427200000,
    "size": "2.5 MB"
  }
}
```

**App 端后续处理（Vue.js）：**

```vue
<script setup>
import { useDatabaseSync } from '@/composables/useDatabaseSync'

const { downloading, progress, downloadDatabase } = useDatabaseSync()

const handleSyncResponse = async (response) => {
  const dbUrl = response.data.data.url
  const success = await downloadDatabase(dbUrl)
  
  if (success) {
    // 重新加载数据或刷新页面
    location.reload()
  }
}
</script>
```

---

## 附录 B: 联系支持

如遇问题，请联系：

- 后端开发：查看项目 `bi-biz` 模块
- 测试工具：参考 `doc/` 目录下的测试脚本
- 日志位置：服务端 `logs/sys-info.log`，App 端本地日志

---

*本文档由代码审计自动生成，如有疑问请以实际代码为准。*
