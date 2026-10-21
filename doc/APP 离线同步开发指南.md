# App 离线同步开发指南

> 文档版本：v1.3  
> 最后更新：2026-10-20  
> 适用对象：App 端开发人员 (Vue.js + Capacitor/Cordova)
>
> **更新说明（v1.3，2026-10-20）：**
> 1. **附件通道（通道A）加固**：`attachments[]` 按 `type` 路由挂载主体、按附件自身 `offlineUuid` 幂等更新（重传不重复）、`offlineDeleted=1` 物理删除已生效（旧版仅跳过插入）、缺 `minioId`/主体反查失败按行报错；病害照片落库后服务端自动重算 `bi_disease.attachment_count`。
> 2. **补齐 JTG/T 5230—2026 新链实体**：载荷新增 `elements`（构件 bi_element）与 `spanElementParts`（构件-桥跨-部件锚定）；病害新增 `elementUuid`、`leafObjectId`、`diseaseTypeId`。旧 `components` 链冻结保留，新规范开发请走新链。
> 3. **父节点支持云端回查**：App 在下发的评定单元（UNIT）下增量建模时，无需重传 UNIT；服务端先查本批、未命中回查云端。
>
> **历史更新：** v1.2 新增离线删除机制和离线更新机制详细说明

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
8. [离线更新机制](#8-离线更新机制)
9. [错误处理](#9-错误处理)
10. [最佳实践](#10-最佳实践)
11. [常见问题](#11-常见问题)

---

## 1. 概述

### 1.1 同步机制说明

本系统采用 **离线优先 (Offline-First)** 架构，App 端在离线环境下采集的数据暂存本地 SQLite，网络恢复后通过同步接口上传至服务端。

**核心设计：**
- 每条离线记录生成唯一 `offlineUuid` (UUID-4)
- 服务端通过 `offlineUuid` 判断是新增还是更新
- 通过 `offlineDeleted=1` 表达删除；各实体删除方式不同（建筑/对象为逻辑删，病害/附件等为物理删，见 §7.3）
- 通过 `is_offline_data` 标识数据来源于离线端
- **逐行容错**：单条数据非法（如附件缺 minioId、主体不存在）只拒绝该行，不影响同批其他数据
- **同步后立即重建 SQLite**：服务端返回全新数据库，App 覆盖本地实现强一致性

### 1.2 数据变更类型支持

| 变更类型 | App 端操作 | 同步字段 | 服务端处理 |
|----------|-----------|----------|-----------|
| **新增** | 插入记录，`offlineDeleted=0` | `offlineUuid`, `offlineDeleted=0` | 插入新记录 |
| **更新** | 修改字段，`offlineDeleted=0` | `offlineUuid`, `offlineDeleted=0` | 根据 UUID 更新 |
| **删除** | 设置 `offlineDeleted=1` | `offlineUuid`, `offlineDeleted=1` | 软删除/物理删除 |

### 1.3 支持同步的实体

**5230 新链（2026 新规范，新开发一律使用）：**

| 实体（载荷键） | 表名 | 说明 |
|------|------|------|
| Building (`buildings`) | `bi_building` | 建筑物/桥梁 |
| BiObject (`objects`) | `bi_object` | 结构树节点：UNIT（评定单元）→SPAN（桥跨）→LAYER（层）→PART（部件） |
| Element (`elements`) | `bi_element` | 新规范构件，挂在 PART 节点上 |
| SpanElementPart (`spanElementParts`) | `bi_span_element_part` | 构件-桥跨-部件锚定关系（一构件可锚定多跨，`isShared` 标记共用构件） |
| Disease (`diseases`) | `bi_disease` | 病害（新链带 `elementUuid`/`leafObjectId`） |
| DiseaseDetail (`diseaseDetails`) | `bi_disease_detail` | 病害详情 |
| Attachment (`attachments`) | `bi_attachment` | 附件/照片（通道A：单文件预上传 + 元数据随 JSON 提交） |

**旧规范链（冻结保留，仅存量数据维护，不要在 5230 功能中使用）：**

| 实体（载荷键） | 表名 | 说明 |
|------|------|------|
| Component (`components`) | `bi_component` | 旧规范构件 |
| SpanComponentPart (`spanComponentParts`) | `bi_span_component_part` | 旧链锚定关系 |

> **新链树构建要点：App 只提交增量节点。** 评定单元 UNIT 随离线包下发、只存在于云端，App 现场新建桥跨/层/部件时，`parentUuid` 直接引用包里 UNIT 的 `offline_uuid` 即可，不要重传 UNIT；服务端解析父节点时本批未命中会自动回查云端（多轮 + DB 回查，见 Q2）。

### 1.4 三类离线包

| 包 | 内容 | 获取方式 |
|----|------|---------|
| common.db（字典包，11 张表） | 桥型模板、叶子部件、病害类型/标度等 5230 字典 | `POST /api/v2/common/sqlite`，随标准升级更新 |
| user.db（用户数据包，11 张表） | 本用户项目下桥梁/结构树/新链构件/病害/照片元数据 | 同步成功响应里直接带 URL，或 `GET/POST /api/v2/user/{id}/sqlite` |
| 单桥 ZIP（7 张表） | 单座桥检查数据（内含 `data.db`）+ 照片文件本体 | `POST /api/v2/building/{id}/package` |

单桥 ZIP 为**交付/归档包**（与同步闭环无关）：内含该桥 7 表的 `data.db`，并把照片文件本体
（优先缩略图）扁平化打进 ZIP——type=1/7 病害照片、type=6 桥梁正/侧立面照、
**type=8 部件当前照片（v1.3 起补齐，按双结构树 PART 节点收集）**。一座桥只保留最新一个包，
重新打包即覆盖；查最近一次包信息用 `GET /api/v2/building/{id}/package`。

病害载荷中的 `leafObjectId`、`diseaseTypeId` 均为 **common.db 字典整数 ID 原样透传**，不做 UUID 映射。

### 1.5 同步策略：Post-Submit Rebuild & Fetch

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
- **删除同步简化**：被删除的记录在新的 SQLite 中不存在，无需本地处理级联删除

---

## 2. 同步流程

### 2.1 完整同步时序

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

### 2.2 步骤说明

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

App 在后台下载新 `.db` 文件，原子替换本地旧数据库（见 §10.6），随后：

- 成功落库的行：新包以服务端状态回灌，本地清掉 `is_offline_data` 标记、保存 ID 映射。
- 被服务端逐行拒绝的行：新包中不存在或回到服务端旧状态，App 应展示失败原因并停止
  自动重试（非法载荷重试也不会成功）；网络层失败则保留离线标记，按 §9.2 重试。

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
  "data": 288  // 文件记录 ID，即 bi_file_map.id，作为附件的 minioId
}
```

**重要约定：**

1. **预上传接口本身不幂等**——每调用一次就会在 MinIO 和 `bi_file_map` 产生一条新文件记录。App 必须在本地缓存返回的 `minioId`（写入 `bi_attachment.minio_id`）；同步重试时**只重发 `attachments[]` 元数据，不要重复上传文件**。
2. 同一张照片更换文件（如重拍）后再次预上传会得到新的 `minioId`，带着相同附件 `offlineUuid` 提交即可，服务端按 `offlineUuid` 更新为新文件，不会产生重复附件行。
3. 缩略图由通道A的元数据字段 `thumbMinioId` 携带（可选），需要时同样先预上传再填 ID。

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
  "components": [...],          // 旧链，5230 新功能留空数组
  "elements": [...],            // 5230 新链构件
  "diseases": [...],
  "diseaseDetails": [...],
  "attachments": [...],
  "spanComponentParts": [...],  // 旧链锚定，5230 新功能留空数组
  "spanElementParts": [...]     // 5230 新链锚定
}
```

> 各实体键均可缺省（按空列表处理），但建议显式传空数组，便于协议自检。

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
          // 注意：code=0 只代表服务端受理整批并重建 user.db，
          // 不代表每行都成功（逐行错误见服务端 bi_sync_log，HTTP 暂不返回明细）。
          // 被拒行会在第 3 步拉回的新包中以服务端状态回灌，App 不应无限重试非法行。
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
      // 删除行（offline_deleted=1）不携带文件，只需随 attachments[] 同步删除标记
      const result = await db.query(
        "SELECT * FROM bi_attachment WHERE is_offline_data = 1 AND offline_deleted = 0")

      for (const att of (result.values || [])) {
        // 预上传不幂等：已有 minio_id 的行（上次已传成功）直接复用，不要重传，
        // 否则会产生孤儿 bi_file_map 且服务端按 offline_uuid 更新为新文件。
        // 仅当用户重拍（local_path 变化）时才清空 minio_id 重新预上传。
        if (att.minio_id) continue

        const fileData = await this.getLocalFile(att.local_path)
        const formData = new FormData()
        formData.append('file', fileData)

        const res = await axios.post('/api/v2/sync/attachment', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })

        if (res.data.code === 0) {
          await db.run('UPDATE bi_attachment SET minio_id = ? WHERE id = ?',
            [res.data.data, att.id])
        } else {
          // 预上传失败的行不要继续提交 sync（服务端会逐行报错且不落库）
          throw new Error('照片预上传失败：' + att.offline_uuid)
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

      // 5230 新链：components/spanComponentParts 已冻结，传 [] 即可
      return {
        syncUuid: this.generateUuid(),
        clientInfo: 'BridgeInspector-Vue/3.0.0',
        buildings: await buildTableData('bi_building'),
        objects: await buildTableData('bi_object'),
        elements: await buildTableData('bi_element'),
        spanElementParts: await buildTableData('bi_span_element_part'),
        diseases: await buildTableData('bi_disease'),
        diseaseDetails: await buildTableData('bi_disease_detail'),
        attachments: await buildTableData('bi_attachment'),
        components: [],
        spanComponentParts: []
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
2. **超时设置**：建议 ≥ 60 秒，照片很多时可到 120 秒
3. **幂等性**：幂等以各实体自身的 `offlineUuid` 为判定依据——同 UUID 重发走更新；照片同附件 UUID 重发只更新 minioId/名称，不产生重复行
4. **逐行容错（非全有或全无）**：所有实体在同一事务内**逐行 try/catch**，某一行失败（如附件缺 minioId、主体反查不到）只记入服务端错误清单，其他行照常落库；只有未预期的致命异常才回滚整个请求
5. **HTTP 响应不含逐行错误**：`code:0` 只表示请求被接受且 user.db 已重打；逐行错误目前只在服务端 `bi_sync_log`/日志的 `SyncResultVo.errors` 中（后续版本计划放进 HTTP 响应）
6. **批次号 `syncUuid`**：仅用于日志和 `bi_id_mapping` 留痕，不参与幂等判定
7. **父引用支持云端回查**：引用此前批次或云端已有实体（如下发的 UNIT、此前同步的病害）无需本批重传

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
  syncUuid?: string;      // 可选，服务端会生成（仅日志/映射留痕）
  clientInfo?: string;    // 可选，客户端标识
  buildings: Building[];
  objects: BiObject[];
  components?: Component[];          // 旧链，5230 新功能传 []
  elements?: Element[];              // 5230 新链构件
  diseases: Disease[];
  diseaseDetails: DiseaseDetail[];
  attachments: Attachment[];
  spanComponentParts?: SpanComponentPart[]; // 旧链锚定，新功能传 []
  spanElementParts?: SpanElementPart[];     // 5230 新链锚定
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
  parentUuid?: string;        // 父节点 UUID (UNIT 根节点传 "0" 或空)
  name: string;
  nodeType: 'UNIT' | 'SPAN' | 'LAYER' | 'PART';  // 节点类型，新链必需
  spanNo?: number;            // nodeType=SPAN 时的跨号
  bridgeType?: string;        // nodeType=SPAN 时的桥型代码，如 "B01"
  status: string;
  templateObjectId?: number;  // PART 绑定桥型模板节点（common.db 字典 ID 直通）
  isOfflineData?: number;
}
```

> 注：新链 `bi_object` 不使用 `buildingUuid`（v1.6 拍板：object→building 归属由桥梁根节点单向定位，采集节点沿父链挂到下发的 UNIT 下即可）。

#### Component（旧链，冻结）

```typescript
interface Component {
  offlineUuid: string;        // 必需
  offlineDeleted?: number;
  objectUuid?: string;        // 所属对象 UUID
  name: string;
  code: string;
  status: string;
  isOfflineData?: number;
}
```

#### Element（5230 新链构件）

```typescript
interface Element {
  offlineUuid: string;        // 必需，病害以字符串 elementUuid 引用它
  offlineDeleted?: number;
  objectUuid: string;         // 所属 PART 节点的 offlineUuid（必需）
  code?: string;              // 构件编号
  name: string;
  materialDurabilityId?: number; // common.db 材料耐久性字典 ID，原样透传
  status: string;
}
```

#### SpanElementPart（5230 新链锚定关系）

```typescript
interface SpanElementPart {
  offlineUuid: string;        // 必需
  offlineDeleted?: number;
  elementUuid: string;        // → elements[].offlineUuid
  spanUuid: string;           // → SPAN 节点 offlineUuid
  partUuid: string;           // → PART 节点 offlineUuid
  isShared?: number;          // 1=共用构件（相邻跨均计入），默认 0
}
```

> 三要素反查任一失败，该行计入逐行错误、不落库（同批其他行不受影响）。服务端自然唯一键
> `(element_id, span_id, part_id)`；同关系 UUID 重发走更新（如改 `isShared`）。

#### Disease

```typescript
interface Disease {
  offlineUuid: string;        // 必需
  offlineDeleted?: number;
  buildingUuid?: string;      // → buildings[].offlineUuid
  objectUuid?: string;        // → PART 节点 offlineUuid
  elementUuid?: string;       // 【新链】→ elements[].offlineUuid（字符串引用，不映射整数 id）
  componentUuid?: string;     // 【旧链】→ components[].offlineUuid，新链不传
  leafObjectId?: number;      // 【新链】归属叶子部件 ID（common.db bi_leaf_object.id 直通）；
                              //   一个 PART 绑多个叶子时必传，用于显式归属
  diseaseTypeId?: number;     // 病害类型 ID（common.db bi_disease_type.id 直通）
  description: string;
  type: string;
  level: number;
  quantity: number;
  commitType?: number;        // 1=离线采集
  taskId?: number;
  images?: string[];          // 图片文件名列表（实际照片走 attachments[]）
  ADImgs?: string[];          // AD 图片列表
  isOfflineData?: number;
}
```

> `attachmentCount` 无需 App 维护：服务端在附件处理完成后按 `bi_attachment` 实际行数
> 对本批受影响的病害自动重算 `attachment_count`（删除照片后同步归零）。

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
  offlineUuid: string;          // 必需，附件自身的稳定 UUID（幂等键）
  offlineDeleted?: number;      // 1=删除服务端既有照片（物理删除，幂等）
  offlineSubjectUuid: string;   // 挂载主体的 offlineUuid，按 type 解释（见下表）
  name?: string;                // 文件名；缺省时服务端兜底 disease_{minioId}.jpg
  type?: number;                // 附件类型（见下表），缺省按 1 病害照片
  minioId: number;              // 【非删除行必需】预上传 /sync/attachment 返回的文件 ID
  thumbMinioId?: number;        // 缩略图文件 ID（可选，先预上传再填）
}
```

**Attachment.type 取值与主体路由（v1.3 起按类型路由）：**

| type 值 | 含义 | `offlineSubjectUuid` 指向 | 是否重算病害 count |
|---------|------|---------------------------|--------------------|
| `1` | 普通病害图片（**推荐**） | `diseases[].offlineUuid` | 是 |
| `7` | 病害 AD 图片 | `diseases[].offlineUuid` | 是 |
| `null`/缺省 | 视同普通病害图片 | `diseases[].offlineUuid` | 是 |
| `6` | 桥梁正/侧立面照片 | `buildings[].offlineUuid` | 否 |
| `8` | 部件当前照片 | PART 节点（`objects[].offlineUuid`，nodeType=PART） | 否 |
| `2`/`5` | 设备附件/标准文档 | 通道A不支持，请勿在 `attachments[]` 提交 | — |

**通道A附件同步规则（务必遵守）：**

1. **先预上传、后提交元数据**：每个文件先调 `POST /api/v2/sync/attachment` 拿 `minioId`，再写入
   `attachments[]`；`minioId` 缺失的非删除行**按行报错、不落库**（数据库 `minio_id` 为 NOT NULL）。
2. **幂等键是附件自身的 `offlineUuid`**：同 UUID 重传执行更新（可改文件/名称/主体），不会产生重复行；
   一张照片从拍摄到重拍始终用同一个 UUID。
3. **删除照片**：提交 `{offlineUuid, offlineDeleted:1}` 即可，无需再带 minioId/主体；
   服务端物理删除既有行（该表无软删除标记），删一条不存在的记录不报错；病害照片删除后
   `attachment_count` 自动重算归零。
4. **主体可在更早的批次同步**：如"先传病害、后补照片"，服务端本批未命中会回查云端，无需重传病害。
5. **主体反查失败按行报错**：`offlineSubjectUuid` 在本批和云端都找不到对应类型实体时，该附件不落库，
   同批其他附件不受影响。
6. **回读**：三类照片的元数据都随 user.db / 单桥包 `data.db` 回传（`bi_attachment` 行 +
   `minio_id → bi_file_map` 指向 MinIO）；其中**单桥 ZIP 还会把三类照片的文件本体打进包内**，
   user.db 只含指针、看图仍需访问 MinIO。

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
| `bi_building` | `root_object_uuid` | → bi_object.offline_uuid（桥梁根节点） |
| `bi_object` | `parent_uuid` | → bi_object.offline_uuid (自引用；根 UNIT 为 `0`/空) |
| `bi_element`（新链） | `object_uuid` | → PART 节点 bi_object.offline_uuid |
| `bi_span_element_part`（新链） | `element_uuid` / `span_uuid` / `part_uuid` | → element / SPAN / PART 的 offline_uuid |
| `bi_component`（旧链） | `object_uuid` | → bi_object.offline_uuid |
| `bi_disease` | `building_uuid` | → bi_building.offline_uuid |
| `bi_disease` | `object_uuid` | → PART 节点 bi_object.offline_uuid |
| `bi_disease` | `element_uuid`（新链） | → bi_element.offline_uuid（字符串引用） |
| `bi_disease` | `component_uuid`（旧链） | → bi_component.offline_uuid |
| `bi_disease` | `leaf_object_id`（新链，**整数非 UUID**） | common.db `bi_leaf_object.id`，原样透传 |
| `bi_disease` | `disease_type_id`（**整数非 UUID**） | common.db `bi_disease_type.id`，原样透传 |
| `bi_disease_detail` | `disease_uuid` | → bi_disease.offline_uuid |
| `bi_attachment` | `offline_subject_uuid` | 按 `type` 分别指向病害 / 桥梁 / PART 的 offline_uuid（见 §4.2 Attachment 表） |

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

-- Element 表（5230 新链构件）
CREATE TABLE bi_element (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    offline_uuid TEXT NOT NULL UNIQUE,
    object_uuid TEXT NOT NULL,       -- → PART 节点
    code TEXT,
    name TEXT NOT NULL,
    status TEXT DEFAULT '0',
    is_offline_data INTEGER DEFAULT 0,
    offline_deleted INTEGER DEFAULT 0
);

-- 构件-桥跨-部件锚定（5230 新链；服务端自然唯一键 element+span+part）
CREATE TABLE bi_span_element_part (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    offline_uuid TEXT NOT NULL UNIQUE,
    element_uuid TEXT NOT NULL,
    span_uuid TEXT NOT NULL,
    part_uuid TEXT NOT NULL,
    is_shared INTEGER DEFAULT 0,
    is_offline_data INTEGER DEFAULT 0,
    offline_deleted INTEGER DEFAULT 0
);

-- Disease 表（新链常用列）
CREATE TABLE bi_disease (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    offline_uuid TEXT NOT NULL UNIQUE,
    building_uuid TEXT,
    object_uuid TEXT,
    element_uuid TEXT,               -- 新链：→ bi_element.offline_uuid
    leaf_object_id INTEGER,          -- 新链：common.db 字典 ID 直通
    disease_type_id INTEGER,         -- common.db 字典 ID 直通
    component_uuid TEXT,             -- 旧链
    description TEXT,
    type TEXT,
    level INTEGER,
    quantity REAL,
    attachment_count INTEGER DEFAULT 0,  -- 服务端按 bi_attachment 重算，App 不必维护
    commit_type INTEGER DEFAULT 1,
    is_offline_data INTEGER DEFAULT 0,
    offline_deleted INTEGER DEFAULT 0
);

-- Attachment 表（注意：服务端该表为物理删除，无 del_flag）
CREATE TABLE bi_attachment (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    offline_uuid TEXT,               -- 云端老附件可为 NULL；离线照片必须有，服务端有唯一键
    offline_subject_uuid TEXT,       -- 按 type 指向病害/桥梁/PART
    name TEXT NOT NULL,
    type INTEGER,
    minio_id INTEGER,                -- 预上传成功后才允许同步
    thumb_minio_id INTEGER,
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
4. 下次同步时，服务端会执行对应的删除操作

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

### 7.3 服务端处理逻辑

服务端收到 `offline_deleted = 1` 的记录后：

```java
// SyncUploadServiceImpl.java 伪代码
if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
    // 记录已存在，执行删除
    if (existing != null) {
        buildingMapper.deleteBuildingById(item.getId());
    }
    // 记录不存在，跳过（已经是删除状态）
    continue;
}
```

**各实体删除处理（v1.3 已与代码核对）：**

| 实体 | 服务端删除处理 | 重传删除是否幂等 |
|------|---------------|------------------|
| Building | 逻辑删除（`del_flag='1'`，`selectByOfflineUuid` 不再命中） | 是 |
| BiObject | 逻辑删除（`del_flag='1'`） | 是 |
| Component（旧链） | 逻辑删除（`del_flag='1'`） | 是 |
| Element（新链） | 逻辑删除（`del_flag='2'`） | 是 |
| Disease | 物理删除 | 是 |
| DiseaseDetail | 物理删除 | 是 |
| SpanComponentPart（旧链） | 物理删除 | 是 |
| SpanElementPart（新链） | 物理删除 | 是 |
| Attachment | **物理删除（v1.3 起生效）**；同时重算所属病害 `attachment_count` | 是，删除不存在的记录不报错 |

> 删除已同步的照片时，载荷只需 `{offlineUuid, offlineSubjectUuid, offlineDeleted:1}`
> （服务端删除前按自身 UUID 查到既有行即可，不强制带 minioId/type）。新包重建后被删照片自然消失。

### 7.4 删除同步时序图

```
App 端                          服务端                          SQLite
 │                               │                               │
 │ 用户删除 Building             │                               │
 │───┐                           │                               │
 │   │ UPDATE bi_building        │                               │
 │   │ SET offline_deleted=1     │                               │
 │<──┘                           │                               │
 │                               │                               │
 │ 下次同步时                    │                               │
 │───┐                           │                               │
 │   │ POST /sync/upload         │                               │
 │   │ { buildings: [{           │                               │
 │   │   offlineUuid: "xxx",     │                               │
 │   │   offlineDeleted: 1       │                               │
 │   │ }] }                      │                               │
 │   │                           │ DELETE FROM bi_building       │
 │   │                           │ WHERE offline_uuid = "xxx"    │
 │   │                           │                               │
 │   │                           │ 重建 SQLite (不含已删记录)     │
 │   │                           │ 上传至 MinIO                  │
 │<──│ { url: "..." }            │                               │
 │   │                           │                               │
 │ 下载新 SQLite                 │                               │
 │ 覆盖本地数据库                │                               │
 │───┐                           │                               │
 │   │ SELECT * FROM ...         │                               │
 │<──┘ (已删除记录不存在)         │                               │
```

### 7.5 注意事项

| 项目 | 说明 |
|------|------|
| **级联删除** | 服务端自动处理，App 端无需关心 |
| **删除幂等性** | 重复删除同一记录不会报错 |
| **网络失败** | 保留 `offline_deleted=1` 标记，等待重试 |
| **UI 刷新** | 删除后立即从列表移除，同步成功后重新加载全量数据 |

---

## 8. 离线更新机制

### 8.1 更新流程

当用户在 App 端修改一条记录时：

1. **直接修改**本地记录字段
2. 确保 `is_offline_data = 1` (标记为待同步)
3. `offline_deleted` 保持为 `0`
4. 下次同步时，服务端根据 `offlineUuid` 找到对应记录并更新

### 8.2 代码示例 (Vue.js)

```javascript
// stores/sync.js - Pinia Store
import { defineStore } from 'pinia'
import { openDB } from '@capacitor-community/sqlite'

export const useSyncStore = defineStore('sync', {
  state: () => ({
    isSyncing: false
  }),
  
  actions: {
    async updateRecord(table, id, changes) {
      const db = await openDB('BridgeInspector', 1, true, false)
      
      // 构建 SET 子句
      const setClause = Object.keys(changes).map(key => `${key} = ?`).join(', ')
      const values = Object.values(changes)
      
      await db.run(
        `UPDATE ${table} SET ${setClause}, is_offline_data = 1 WHERE id = ?`,
        [...values, id]
      )
      
      // 标记为待同步
      await this.addSyncQueue(table, id)
    },
    
    async addSyncQueue(table, id) {
      const queue = JSON.parse(localStorage.getItem('sync_queue') || '[]')
      // 避免重复入队
      const exists = queue.some(item => item.table === table && item.id === id)
      if (!exists) {
        queue.push({ table, id, timestamp: Date.now() })
        localStorage.setItem('sync_queue', JSON.stringify(queue))
      }
    }
  }
})

// 使用示例
// components/BuildingDetail.vue
<script setup>
import { useSyncStore } from '@/stores/sync'

const syncStore = useSyncStore()

const saveBuilding = async (building) => {
  await syncStore.updateRecord('bi_building', building.id, {
    name: building.name,
    area: building.area,
    status: building.status,
    update_time: new Date().toISOString()
  })
}
</script>
```

### 8.3 服务端处理逻辑

服务端收到更新请求后：

```java
// SyncUploadServiceImpl.java 伪代码
Building existing = buildingMapper.selectByOfflineUuid(item.getOfflineUuid());
if (existing != null) {
    // 记录已存在，执行更新
    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
        // 标记为删除
        buildingMapper.deleteBuildingById(item.getId());
    } else {
        // 正常更新
        item.setId(existing.getId());
        buildingMapper.updateBuilding(item);
    }
    uuidMap.put(item.getOfflineUuid(), existing.getId());
    continue;
}
// 记录不存在，且不是删除操作 → 插入新记录
```

### 8.4 更新字段说明

**可更新字段：**

| 实体 | 可更新字段 | 说明 |
|------|-----------|------|
| Building | `name`, `area`, `line`, `status`, `longitude`, `latitude` 等 | 除 ID、UUID 外的业务字段 |
| BiObject | `name`, `status`, `nodeType`, `spanNo`, `bridgeType` 等 | 除 ID、UUID、父级引用外的字段 |
| Element（新链） | `code`, `name`, `materialDurabilityId`, `status` 等 | 挂 PART 的 `objectUuid` 一般不变 |
| Disease | `description`, `type`, `level`, `quantity`, `elementUuid`, `leafObjectId`, `diseaseTypeId` | 病害信息与归属；`attachmentCount` 由服务端重算，App 不要写 |
| DiseaseDetail | 所有测量字段 | `width`, `length1`, `reference1Location` 等 |
| Attachment | `minioId`, `name`, `type`, `offlineSubjectUuid` | 重拍复用同 `offlineUuid`+新 `minioId` 即更新 |
| ~~Component（旧链）~~ | `name`, `code`, `status` | 冻结维护；旧版 `edi/efi/eai` 评定列随 v2 引擎下线不再维护 |

**不可更新字段：**

| 字段 | 说明 |
|------|------|
| `id` | 服务端主键，由服务端分配 |
| `offline_uuid` | 离线唯一标识，生成后不可变 |
| `is_offline_data` | 同步状态标志，服务端自动管理 |
| `create_time`, `create_by` | 创建信息，不可变 |

### 8.5 冲突处理

当前版本采用 **"服务端优先"** 策略：

- 服务端根据 `offlineUuid` 找到记录直接覆盖更新
- 不比较变更时间戳
- 不合并多方变更

**建议实践：**
- 同一记录避免多设备同时编辑
- 同步成功后，App 立即刷新本地数据（通过下载新 SQLite）
- 编辑前检查网络状态，在线时直接调用在线编辑接口

### 8.6 更新同步时序图

```
App 端                          服务端                          SQLite
 │                               │                               │
 │ 用户修改 Building 名称        │                               │
 │───┐                           │                               │
 │   │ UPDATE bi_building        │                               │
 │   │ SET name='新名称',         │                               │
 │   │     is_offline_data=1     │                               │
 │   │ WHERE id=123              │                               │
 │<──┘                           │                               │
 │                               │                               │
 │ 下次同步时                    │                               │
 │───┐                           │                               │
 │   │ POST /sync/upload         │                               │
 │   │ { buildings: [{           │                               │
 │   │   offlineUuid: "xxx",     │                               │
 │   │   name: "新名称",          │                               │
 │   │   offlineDeleted: 0       │                               │
 │   │ }] }                      │                               │
 │   │                           │ SELECT id FROM bi_building    │
 │   │                           │ WHERE offline_uuid = "xxx"    │
 │   │                           │ → id=123                      │
 │   │                           │                               │
 │   │                           │ UPDATE bi_building            │
 │   │                           │ SET name='新名称'              │
 │   │                           │ WHERE id=123                  │
 │   │                           │                               │
 │   │                           │ 重建 SQLite (含更新后数据)     │
 │   │                           │ 上传至 MinIO                  │
 │<──│ { url: "..." }            │                               │
 │   │                           │                               │
 │ 下载新 SQLite                 │                               │
 │ 覆盖本地数据库                │                               │
 │───┐                           │                               │
 │   │ SELECT name FROM ...      │                               │
 │<──┘ → "新名称"                 │                               │
```

---

## 9. 错误处理

### 9.1 常见错误码

| code | 说明 | 处理建议 |
|------|------|---------|
| 0 | 成功 | 更新本地状态 |
| 200 | 成功 | 同上 |
| 401 | 认证失败 | 刷新 Token 后重试 |
| 500 | 服务端错误 | 记录日志，等待人工介入 |

### 9.2 重试策略

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

### 9.3 部分失败处理（重要：逐行容错）

服务端**逐行 try/catch**：个别行失败（附件缺 minioId、锚定主体反查不到等）只累加
`SyncResultVo.failureCount/errors`，同批其他行照常落库，HTTP 状态仍为 200、`code:0`。

**当前可见性限制：** `/api/v2/sync/upload` 的 HTTP 响应固定为新 user.db 的 `{url,timestamp,size}`，
**暂不包含** `successCount/failureCount/errors`（服务端已算出但 Controller 未透传，后续版本开放）。
排查逐行失败的途径：

1. 服务端 `bi_sync_log`（按 `sync_uuid`/`client_info` 定位批次状态与异常）
2. 服务端日志中该批次的逐行错误（实体类型 + offlineUuid + 原因）
3. 同步后下载新 user.db 核对：失败的行不会出现/未更新

**App 侧建议：**

1. 成功出现的记录：更新本地 `is_offline_data = 0`（以新包内容为准做覆盖）
2. 怀疑失败时（如新包中找不到某条记录）：保留待同步标记，下批重试；重试整体幂等
3. 附件类失败多为可自愈问题（预上传失败导致缺 minioId、病害本身尚未同步成功），补做预上传/调整顺序后重发即可

---

## 10. 最佳实践

### 10.1 UUID 生成 (Vue.js)

**⚠️ 重要：UUID 重复风险**

文档中提供的 `generateUuid()` 使用 `Math.random()`，**在极端情况下可能重复**。可能导致：
- 同步时数据覆盖
- 服务端拒绝请求（UUID 冲突）

**推荐方案（按优先级）：**

**方案 1：使用第三方库（推荐）**

```bash
npm install uuid
```

```javascript
// utils/uuid.js
import { v4 as uuidv4 } from 'uuid'

export function generateUuid() {
  return uuidv4()  // 标准的 UUID-4，碰撞概率极低
}
```

**方案 2：使用 Crypto API（浏览器/WebView）**

```javascript
// 适用于现代浏览器和 Capacitor WebView
export function generateSecureUuid() {
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  // 设置 UUID-4 版本位
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  // 设置 RFC 4122 变体位
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  
  const hex = [...bytes].map(b => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`
}
```

**方案 3：简单实现（仅用于测试/临时）**

```javascript
export function generateUuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
  })
}
```

**使用示例：**

```javascript
import { generateUuid } from '@/utils/uuid'

// 生成唯一 ID
const uuid = generateUuid()
// 示例输出："550e8400-e29b-41d4-a716-446655440000"
```

**注意事项：**

| 项目 | 说明 |
|------|------|
| 格式 | UUID-4（32 位十六进制，带连字符） |
| 大小写 | 建议统一使用小写 |
| 存储 | SQLite 中使用 `TEXT` 类型 |
| 唯一性 | 每条离线记录必须唯一，重复会导致同步失败 |

### 10.2 同步粒度

- 建议每 **50-100 条记录** 分批同步
- 单次请求 JSON 大小建议不超过 **5MB**
- 附件上传建议并发数 ≤ 3

### 10.3 本地状态管理

建议增加本地状态字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sync_status` | TEXT | `pending` / `synced` / `failed` |
| `sync_error` | TEXT | 失败原因 |
| `sync_time` | DATETIME | 最后同步时间 |
| `server_id` | INTEGER | 服务端 ID (同步后填充) |

### 10.4 网络优化

- 使用 **WiFi 优先** 策略
- 图片上传前进行 **压缩** (建议 ≤ 500KB)
- 弱网环境下降低并发数

### 10.5 日志记录

建议记录以下日志用于排查：

```
[Sync] 开始同步，UUID: xxx, 记录数：42
[Sync] 附件上传：0_front_1.jpg → serverId: 288
[Sync] 提交载荷，大小：1.2MB
[Sync] 服务端响应：success=42, failed=0
[Sync] 开始下载新数据库...
[Sync] 数据库替换完成
```

### 10.6 数据库下载策略 (重要)

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

## 11. 常见问题

### Q1: 同步时提示"UUID 重复"

**原因：** 两条本地记录生成了相同的 UUID，或重复提交了已同步的记录。

**解决：**
- 检查 UUID 生成逻辑
- 同步成功后立即标记 `is_offline_data = 0`

### Q2: BiObject 同步后父子关系丢失

**原因：** `parentUuid` 指向的节点既不在本批载荷中，云端也查不到。

**解决：** v1.3 起服务端对 `bi_object` 做最多 5 轮乱序消解，本批未命中还会回查云端既有节点——
App 在下发的 UNIT 下增量建 SPAN/LAYER/PART 时直接引用 UNIT 的 `offline_uuid` 即可，无需重传整棵树。
若 5 轮后父节点仍不存在（UUID 拼写错误或父节点从未成功同步），该节点计入逐行错误不落库，修正 UUID 后重发。

### Q3: 图片上传成功但同步失败

**原因：** 附件预上传与数据同步是两个独立步骤，文件已在 MinIO/`bi_file_map`，但附件元数据未落库。

**解决：**
- 预上传返回的 `minioId` 已缓存在本地时，**不要重新上传文件**，带着相同附件 `offlineUuid`+`minioId` 重发 `attachments[]` 即可（幂等）。
- 若错误原因为"挂载主体反查失败"，先确保病害/桥梁/PART 已同步成功，再重发附件（服务端会回查云端，不要求同批）。
- 若错误原因为"缺少 minioId"，说明本地未记录预上传结果，需要重新预上传该照片。
- 孤立的 `bi_file_map` 记录不影响业务；服务端暂无清理接口。

### Q3b: 重拍/更换病害照片怎么处理？

对同一张逻辑照片**复用附件 `offlineUuid`**：预上传新文件拿到新 `minioId`，再提交一次
`attachments[]`（同 offlineUuid、新 minioId），服务端更新既有行，`attachment_count` 不翻倍。
删除旧照片则提交 `offlineDeleted:1`（物理删除、count 自动重算）。

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

### Q8: 离线删除后，本地列表如何刷新？

**建议做法：**

```javascript
// 1. 删除标记后立即从 UI 列表移除
const deleteBuilding = async (building) => {
  await syncStore.markAsDeleted('bi_building', building.id)
  buildings.value = buildings.value.filter(b => b.id !== building.id)
}

// 2. 同步成功后，重新加载全量数据
const handleSyncSuccess = async () => {
  await loadBuildingsFromDatabase() // 从新 SQLite 重新加载
}
```

**原因：** 同步成功后，App 会下载全新的 SQLite，已删除的记录不存在于新数据库中。

### Q9: 云端数据被离线删除后，`is_offline_data` 如何设置？

当用户删除一条原本来自云端的记录（`is_offline_data = 0`）时：

```sql
UPDATE bi_building 
SET offline_deleted = 1, is_offline_data = 1 
WHERE id = ?
```

**说明：**
- `offline_deleted = 1` 告知服务端需要删除
- `is_offline_data = 1` 标记为待同步，确保记录会被上传

### Q10: 删除操作需要重试吗？

**需要。** 删除操作和新增/更新使用相同的同步队列和重试机制：

```javascript
// 删除失败后，记录仍保留 offline_deleted=1
// 下次同步时会再次尝试上传
```

**注意：** 服务端删除是幂等的，重复删除同一记录不会报错。

---

## 附录 A: 完整同步示例

### 请求示例

下例为 5230 新链：App 在下发的评定单元 UNIT（`std5230demo-b01-unit`，仅云端存在）下
现场新增一跨一层一部件 + 构件 + 锚定 + 病害（含叶子归属）+ 详情 + 三类照片。
`leafObjectId`/`diseaseTypeId` 为 common.db 字典整数 ID；照片的 `minioId` 来自预上传响应。

```json
{
  "syncUuid": "550e8400-e29b-41d4-a716-446655440000",
  "clientInfo": "BridgeInspector-Vue/3.0.0",
  "buildings": [
    {
      "offlineUuid": "b1-uuid-xxx",
      "name": "学海桥",
      "area": "武汉市",
      "line": "二环线",
      "status": "0",
      "isLeaf": "1",
      "rootObjectUuid": "std5230demo-b01-unit",
      "offlineDeleted": 0
    }
  ],
  "objects": [
    {
      "offlineUuid": "o-span-xxx",
      "parentUuid": "std5230demo-b01-unit",
      "nodeType": "SPAN",
      "name": "第1跨",
      "spanNo": 1,
      "bridgeType": "B01",
      "status": "0",
      "offlineDeleted": 0
    },
    {
      "offlineUuid": "o-layer-xxx",
      "parentUuid": "o-span-xxx",
      "nodeType": "LAYER",
      "name": "上部结构",
      "status": "0",
      "offlineDeleted": 0
    },
    {
      "offlineUuid": "o-part-xxx",
      "parentUuid": "o-layer-xxx",
      "nodeType": "PART",
      "name": "钢主梁",
      "status": "0",
      "offlineDeleted": 0
    }
  ],
  "components": [],
  "elements": [
    {
      "offlineUuid": "e1-uuid-xxx",
      "objectUuid": "o-part-xxx",
      "code": "EL-001",
      "name": "1号主梁构件",
      "status": "0",
      "offlineDeleted": 0
    }
  ],
  "spanElementParts": [
    {
      "offlineUuid": "r1-uuid-xxx",
      "elementUuid": "e1-uuid-xxx",
      "spanUuid": "o-span-xxx",
      "partUuid": "o-part-xxx",
      "isShared": 0,
      "offlineDeleted": 0
    }
  ],
  "diseases": [
    {
      "offlineUuid": "d1-uuid-xxx",
      "buildingUuid": "b1-uuid-xxx",
      "objectUuid": "o-part-xxx",
      "elementUuid": "e1-uuid-xxx",
      "leafObjectId": 18,
      "diseaseTypeId": 301,
      "description": "梁体裂缝",
      "type": "裂缝",
      "level": 2,
      "quantity": 3,
      "commitType": 1,
      "offlineDeleted": 0
    }
  ],
  "diseaseDetails": [
    {
      "offlineUuid": "dd1-uuid-xxx",
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
      "name": "disease_crack_1.jpg",
      "type": 1,
      "minioId": 288,
      "offlineDeleted": 0
    },
    {
      "offlineUuid": "a2-uuid-xxx",
      "offlineSubjectUuid": "b1-uuid-xxx",
      "name": "newfront_bridge.jpg",
      "type": 6,
      "minioId": 289,
      "offlineDeleted": 0
    },
    {
      "offlineUuid": "a3-uuid-xxx",
      "offlineSubjectUuid": "o-part-xxx",
      "name": "biObject_girder.jpg",
      "type": 8,
      "minioId": 290,
      "offlineDeleted": 0
    }
  ],
  "spanComponentParts": []
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
