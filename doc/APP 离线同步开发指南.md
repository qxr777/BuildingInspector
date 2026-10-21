# App 离线同步开发指南

> 文档版本：v1.4  
> 最后更新：2026-10-21  
> 适用对象：App 端开发人员 (Vue.js + Capacitor/Cordova)
>
> **更新说明（v1.4，2026-10-21）：通道A 附件预上传提速（渐进式增强，新旧 App 可共存）**
> 1. **预上传幂等化**：新增客户端幂等键 `uploadUuid`（拍照产出时生成、持久化、重试复用，重拍才换新）。
>    同 uuid 重放返回 `status=exists` 与既有 `minioId/thumbMinioId`，**零重复流量**，响应丢失/超时可安全重试。
> 2. **新增批量与回查接口**：`POST /sync/attachment/batch`（一批多文件，逐项容错）与
>    `POST /sync/attachment/query`（同步前/响应丢失后回查已传清单）。推荐流程：先 query 跳过已传 →
>    分批 batch（建议 ≤8 张/批或 ≤10MB，批间并发 ≤3）。
> 3. **缩略图改由服务端自动生成**：图片预上传成功即同步返回 `thumbMinioId`，App 无需自行上传缩略图；
>    非图片或生成失败时为 `null`。
> 4. **拍照压缩要求明确**：长边 ≤2000px、JPEG 质量 ≈0.8、目标单张 ≤1MB（服务端硬上限 20MB/张、
>    20 张/批仅兜底）；服务端原样存储，不二次重编码。
> 5. **旧接口完全保留**：`POST /sync/attachment` 不带 `uploadUuid` 时响应仍是裸 `minioId`，
>    旧版 App 不改代码可继续工作（但不享受幂等/缩略图/批量能力）。
>
> **历史更新：**
> - v1.3（2026-10-20）：附件通道（通道A）加固——`attachments[]` 按 `type` 路由挂载主体、按附件自身 `offlineUuid` 幂等更新、`offlineDeleted=1` 物理删除生效、缺 `minioId`/主体反查失败按行报错、病害照片落库后自动重算 `bi_disease.attachment_count`；补齐 JTG/T 5230—2026 新链实体（`elements`/`spanElementParts`，病害新增 `elementUuid`/`leafObjectId`/`diseaseTypeId`）；父节点支持云端回查。
> - v1.2：新增离线删除机制和离线更新机制详细说明。

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
- **附件预上传幂等 + 批量**（v1.4）：照片以 `uploadUuid` 幂等预上传（同 uuid 重放零重复流量），
  支持批量上传与回查，缩略图由服务端自动生成；旧版裸 minioId 单文件接口永久保留
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
     │ 1. 拍照即压缩(长边≤2000/q0.8)，生成并持久化 uploadUuid
     │                   │                   │
     │ 2. POST /sync/attachment/query        │  同步前回查已传清单
     │──────────────────────────────────────>│
     │ 3. 返回各项 uploaded/minioId（跳过已传）│
     │<──────────────────────────────────────│
     │                   │                   │
     │ 4. 分批 POST /sync/attachment/batch    │  未传项分批上传(建议≤8张/批)
     │    files[] + uploadUuids[]（交错对齐） │  批间并发≤3
     │──────────────────────────────────────>│
     │                   │                   │ 服务端存原图+自动生成缩略图
     │ 5. 返回逐项 minioId/thumbMinioId       │
     │<──────────────────────────────────────│
     │                   │                   │
     │ 6. 构造 JSON 载荷（两个 id 回填到 attachments[]）
     │                   │                   │
     │ 7. POST /sync/upload                  │
     │──────────────────────────────────────>│
     │                   │                   │
     │ 8. 处理并落库      │                   │
     │ 9. 重建用户 SQLite │                   │
     │ 10. 上传至 MinIO   │                   │
     │                   │                   │
     │ 11. 返回下载地址   │                   │
     │<──────────────────────────────────────│
     │   { url, timestamp, size }            │
     │                   │                   │
     │ 12. 后台下载新.db  │                   │
     │──────────────────>│                   │
     │                   │                   │
     │ 13. 替换本地文件   │                   │
     │                   │                   │
```

> 旧版 App（不带 `uploadUuid`，逐张调 `/sync/attachment`）的时序仍是第 4、5 步的单文件版本，
> 服务端永久兼容；步骤 2、3 的回查与批量是 v1.4 新增能力，建议新版 App 采用。

### 2.2 步骤说明

**步骤 1：拍照压缩并生成 uploadUuid（v1.4 推荐）**

照片在拍摄/保存时即完成端侧压缩——**长边 ≤2000px、JPEG 质量 ≈0.8、目标单张 ≤1MB**
（服务端原样存储、不二次重编码；20MB 为服务端硬上限，仅作兜底不要贴着用）。
同时为**这一次文件产出**生成 `uploadUuid`（32 位去横线 UUID，与附件 `offlineUuid` 相互独立）并持久化：

- 上传失败/响应丢失/超时重试：**复用同一个 uploadUuid**，服务端幂等返回既有结果，不产生重复文件；
- 用户**重拍或更换照片文件**：生成**新的 uploadUuid**，附件 `offlineUuid` 保持不变；
- 建议本地 `bi_attachment` 增加 `upload_uuid TEXT` 列保存（见 §5.3）。

**步骤 2：回查已传清单，跳过已完成项**

同步前（或上次同步中途失败后）先批量回查，只上传服务端还没有的文件：

```
POST /api/v2/sync/attachment/query
Content-Type: application/json

{ "uploadUuids": ["uuid1", "uuid2", "..."] }   // 单次 ≤500 个
```

对返回中 `uploaded=true` 的项，直接把 `minioId/thumbMinioId` 写回本地，不再上传。

**步骤 3：分批预上传未完成项**

将剩余文件按 **≤8 张/批或整批 ≤10MB** 分组（服务端硬上限 20 张/批），**批间并发 ≤3**，
批内由服务端顺序处理。`FormData` 中 `files` 与 `uploadUuids` 必须按下标对齐，建议交错 append：

```
POST /api/v2/sync/attachment/batch
Content-Type: multipart/form-data
```

逐项结果在 `data.items[]` 中返回（HTTP 200/code=0 也可能包含失败项，必须逐项判断 `status`）：

```json
{
  "code": 0,
  "msg": "批量预上传完成",
  "data": {
    "total": 2, "successCount": 1, "existsCount": 0, "failureCount": 1,
    "items": [
      { "uploadUuid": "uuid1", "status": "success",
        "minioId": 12345, "thumbMinioId": 12346 },
      { "uploadUuid": "uuid2", "status": "failed", "error": "文件为空" }
    ]
  }
}
```

- `status`：`success`（本次新传）/ `exists`（幂等命中，未重复上传）/ `failed`（看 `error`）；
- **缩略图由服务端自动生成**，图片类型会同时返回 `thumbMinioId`，无需 App 另行上传；
  非图片或缩略图生成失败时为 `null`，原图仍算成功；
- 失败项保留 `upload_uuid` 与离线标记，下次同步先 query 再补传，**不要换 uuid**。

单张上传也可继续使用增强模式单文件接口 `POST /sync/attachment`（带 `uploadUuid` form 字段，
`data` 为单个 item 对象）；**不带 `uploadUuid` 时是旧版兼容模式**，`data` 为裸 minioId。

**步骤 4：更新本地附件记录**

将返回的 `minioId`、`thumbMinioId` 分别写入本地 `bi_attachment.minio_id`、
`bi_attachment.thumb_minio_id`，随后在 `attachments[]` 中一并提交（见 §4.2）。

**步骤 5：构造同步载荷**

提取所有 `is_offline_data = 1` 且未同步的业务数据，构造 JSON（详见第 4 节）。

**步骤 6：提交同步**

```
POST /api/v2/sync/upload
Authorization: Bearer {token}
Content-Type: application/json

{ ...payload... }
```

**步骤 7-9：服务端处理（自动）**

服务端自动完成：
1. 解析 JSON 并落库
2. 合并 ID 映射
3. **重建用户专属 SQLite**（包含所有最新数据）
4. 上传至 MinIO

**步骤 10：返回下载地址**

响应中包含完整 SQLite 的下载 URL。

**步骤 11-13：App 下载并替换**

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

### 3.1 附件预上传（共三种形态 + 回查）

通道 A 提供四个端点，认证均需要 JWT：

| 端点 | 形态 | 响应 `data` | 幂等 | 缩略图 |
|------|------|------------|------|--------|
| `POST /sync/attachment`（不带 `uploadUuid`） | **旧版兼容** | 裸 `minioId`（Integer） | 否 | 不生成 |
| `POST /sync/attachment`（带 `uploadUuid`） | 增强单文件 | item 对象 | 是 | 自动生成 |
| `POST /sync/attachment/batch` | **推荐**：批量 | `{total,...,items[]}` | 是 | 自动生成 |
| `POST /sync/attachment/query` | 回查已传 | status 列表 | — | — |

通用约束：单文件硬上限 **20MB**、单批文件数硬上限 **20**（建议 App 侧 ≤8 张/批或 ≤10MB）；
`uploadUuid` 为 32 位十六进制（UUID 去横线，大小写/横线服务端会归一化，但建议统一小写无横线）。

#### 3.1.1 增强模式：单文件预上传

```http
POST /api/v2/sync/attachment
Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...
Content-Type: multipart/form-data

file: [binary]
uploadUuid: 4f8a5d2c9b7e4f1a8c3d6e5b0a2947d1
```

**响应（成功 / 幂等命中）：**
```json
{
  "code": 0,
  "msg": "上传成功",
  "data": {
    "uploadUuid": "4f8a5d2c9b7e4f1a8c3d6e5b0a2947d1",
    "status": "success",          // success=本次新传；exists=幂等命中（未重复上传）
    "minioId": 288,               // 原图 bi_file_map.id
    "thumbMinioId": 289,          // 缩略图 id；非图片或生成失败为 null
    "error": null
  }
}
```

单项失败时 HTTP 信封为 `code=500`，`msg` 含失败原因（如 `文件为空`、`超出单文件大小限制`、
`uploadUuid 非法（需为32位UUID）`、`服务端登记状态不一致，请联系管理员`）。

#### 3.1.2 批量预上传（推荐）

```http
POST /api/v2/sync/attachment/batch
Content-Type: multipart/form-data
```

| part 名 | 类型 | 说明 |
|---------|------|------|
| `files` | file，可重复 | 1..20 个文件 part |
| `uploadUuids` | text，可重复 | 与 `files` **等长、按下标对齐**；建议交错 append 防错位 |

**请求级错误**（整请求 `code=500`，没有任何文件被处理）：`files` 为空、
`uploadUuids` 与 `files` 数量不一致、文件数超过 20。

**逐项成败收敛在 `data.items[]`**：HTTP 200/`code=0` 也可能包含失败项，App 必须逐项看 `status`；
单项失败不影响同批其他文件。`items[]` 顺序与请求下标一致，计数含
`total/successCount/existsCount/failureCount`。单项 `status` 同 3.1.1（success/exists/failed），
失败项的典型 `error`：`文件为空`、`超出单文件大小限制`、`uploadUuid 非法（需为32位UUID）`、
`批内 uploadUuid 重复`（同批出现两个相同 uuid，只有第一项会被处理）。

FormData 组包示例（交错 append）：

```javascript
const form = new FormData()
for (const att of pending) {
  form.append('files', att.file, att.name)
  form.append('uploadUuids', att.uploadUuid)
}
const res = await axios.post('/api/v2/sync/attachment/batch', form, {
  headers: { 'Content-Type': 'multipart/form-data' }
})
```

#### 3.1.3 回查已传附件

同步开始前或上传响应丢失后，用回查代替盲目重传：

```http
POST /api/v2/sync/attachment/query
Content-Type: application/json

{ "uploadUuids": ["4f8a...", "9c2b..."] }
```

- 请求体为空 / `uploadUuids` 为空 / 单次超过 500 个 → `code=500`；
- 响应 `data` 为数组，**顺序与请求一致**（含重复/非法 uuid，非法项按 `uploaded=false` 返回）：

```json
{
  "code": 0,
  "msg": "查询成功",
  "data": [
    { "uploadUuid": "4f8a...", "uploaded": true,  "minioId": 288, "thumbMinioId": 289 },
    { "uploadUuid": "9c2b...", "uploaded": false, "minioId": null, "thumbMinioId": null }
  ]
}
```

#### 3.1.4 旧版兼容模式（不带 uploadUuid）

不带 `uploadUuid` form 字段（或空串）时，行为与历史版本**完全一致**：服务端不登记、不生成缩略图，
每次调用都产生一条新的 `bi_file_map`，响应 `data` 为裸整数：

```json
{ "code": 0, "msg": "上传成功", "data": 288 }
```

旧版 App 可不升级继续使用；新版 App 不应再走该模式（无法幂等重试、无缩略图）。

**重要约定：**

1. **uploadUuid 标识"一次文件产出"，不是附件身份**：附件身份仍是附件自身的 `offlineUuid`。
   上传/同步任何阶段失败重试都复用同一 `uploadUuid`；只有用户重拍、替换文件才换新 uuid。
2. `exists` 与 `success` 对 App 完全等价——拿到的 `minioId` 都可直接使用，不要因为 exists 而重传。
3. 重拍照片：用**新 uploadUuid** 预上传得到新 `minioId`，带着**相同附件 `offlineUuid`** 提交
   `attachments[]`，服务端按 `offlineUuid` 更新为新文件，不产生重复附件行。
4. 缩略图**不需要 App 自行上传**：图片类文件服务端自动生成并返回 `thumbMinioId`；
   `null` 表示非图片或本次生成失败（原图仍成功），按无缩略图处理即可。

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
    
    // v1.4：query 回查 → 分批 batch 上传（≤8 张/批，批间并发 ≤3）→ 双 id 回写本地
    async uploadAttachments() {
      const db = await openDB('BridgeInspector', 1, true, false)
      // 删除行（offline_deleted=1）不携带文件，只需随 attachments[] 同步删除标记
      const result = await db.query(
        "SELECT * FROM bi_attachment WHERE is_offline_data = 1 AND offline_deleted = 0")
      let rows = result.values || []

      // ① 回查：已在服务端登记的（上次响应丢失/同步中断）直接取回双 id，零流量
      const haveUuid = rows.filter(r => r.upload_uuid)
      if (haveUuid.length) {
        const known = await this.queryUploaded(haveUuid.map(r => r.upload_uuid))
        for (const st of known) {
          if (st.uploaded) {
            await db.run(
              'UPDATE bi_attachment SET minio_id = ?, thumb_minio_id = ? WHERE upload_uuid = ?',
              [st.minioId, st.thumbMinioId, st.uploadUuid])
          }
        }
      }

      // ② 仅上传本地仍缺 minio_id 的文件；重拍时拍照流程应已换新 upload_uuid
      const pending = []
      for (const r of rows) {
        if (!r.minio_id) {
          if (!r.upload_uuid) throw new Error('附件缺少 upload_uuid：' + r.offline_uuid)
          pending.push({ row: r, file: await this.getLocalFile(r.local_path) })
        }
      }

      // ③ 分批：≤8 张/批，批间并发 ≤3（可用简易 p-limit 池）
      const BATCH = 8
      for (let i = 0; i < pending.length; i += BATCH) {
        const group = pending.slice(i, i + BATCH)
        const form = new FormData()
        for (const p of group) {
          form.append('files', p.file, p.row.name || 'photo.jpg')
          form.append('uploadUuids', p.row.upload_uuid)   // 交错 append，与 files 下标对齐
        }
        const res = await axios.post('/api/v2/sync/attachment/batch', form, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        if (res.data.code !== 0) {
          // 请求级错误（空批/数量错位/超限）：本批一张都没传，整轮中止等下次
          throw new Error('照片批量预上传失败：' + res.data.msg)
        }
        const items = res.data.data.items
        for (let idx = 0; idx < items.length; idx++) {
          const item = items[idx]
          if (item.status === 'failed') {
            // 逐项失败：保留 upload_uuid 与离线标记，下次 query 后补传，不要换新 uuid
            console.warn('照片预上传失败：', group[idx].row.offline_uuid, item.error)
            continue
          }
          await db.run(
            'UPDATE bi_attachment SET minio_id = ?, thumb_minio_id = ? WHERE id = ?',
            [item.minioId, item.thumbMinioId, group[idx].row.id])
        }
      }

      // ④ 仍有失败项则不进入 sync/upload（缺 minioId 的行会被服务端逐行拒绝）
      const left = await db.query(
        "SELECT COUNT(1) AS c FROM bi_attachment WHERE is_offline_data = 1 " +
        "AND offline_deleted = 0 AND minio_id IS NULL")
      if (left.values[0].c > 0) throw new Error('仍有照片未预上传成功，已保留待下次同步')
    },

    async queryUploaded(uploadUuids) {
      // 单次 ≤500；现场量很小，这里从略分片
      const res = await axios.post('/api/v2/sync/attachment/query', { uploadUuids })
      if (res.data.code !== 0) throw new Error('附件回查失败：' + res.data.msg)
      return res.data.data
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
3. **幂等性**：业务幂等以各实体自身的 `offlineUuid` 为判定依据——同 UUID 重发走更新；照片同附件 UUID 重发只更新 minioId/名称，不产生重复行。**文件预上传另有独立幂等键 `uploadUuid`**（v1.4，见 §3.1）：同 uuid 重放零重复流量，两套键互不替代
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
  minioId: number;              // 【非删除行必需】预上传返回的原图文件 ID（bi_file_map.id）
  thumbMinioId?: number;        // 缩略图 ID：v1.4 起由服务端预上传时自动生成并返回，App 直接回填；
                                // null/缺省=非图片或服务端本次生成失败，不影响原图落库
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

1. **先预上传、后提交元数据**：每个文件先经 `POST /api/v2/sync/attachment/batch`
   （或增强模式单文件接口）拿到 `minioId`/`thumbMinioId`，再写入 `attachments[]`；
   `minioId` 缺失的非删除行**按行报错、不落库**（数据库 `minio_id` 为 NOT NULL）。
   预上传以 `uploadUuid` 幂等（见 §3.1），同步前可先 query 回查跳过已传项。
2. **两类 UUID 各司其职**：附件的业务身份是附件自身的 `offlineUuid`（同 UUID 重传执行更新，
   可改文件/名称/主体，不产生重复行；一张照片从拍摄到重拍始终用同一个 `offlineUuid`）；
   `uploadUuid` 只标识"一次文件产出"，用于预上传幂等——重试复用、重拍换新。
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
    thumb_minio_id INTEGER,          -- 服务端自动生成，随批量/增强响应回填
    upload_uuid TEXT,                -- v1.4：本次文件产出的幂等键（32位hex），拍照时生成、重试复用、重拍换新
    is_offline_data INTEGER DEFAULT 0,
    offline_deleted INTEGER DEFAULT 0
);
```

> **`upload_uuid` 生命周期（v1.4）：**
> ① 拍照/选择文件并压缩保存时生成，与附件行一起持久化；
> ② 预上传、query 回查、任何失败重试全程**复用同一个值**，服务端据此保证零重复流量；
> ③ 用户**重拍或替换文件**时生成新 uuid（附件 `offline_uuid` 不变），旧 uuid 自然废弃；
> ④ 该列只在 App 本地使用、不参与 `/sync/upload` 载荷（服务端登记表只认预上传时的 form 字段）。

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
3. 附件类失败多为可自愈问题（预上传失败导致缺 minioId、病害本身尚未同步成功）：v1.4 起先 query 回查（响应可能只是丢了），再补做缺失项的预上传，调整顺序后重发即可

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
- 附件批量预上传：**每批 ≤8 张或整批 ≤10MB**（服务端硬上限 20 张/批、20MB/张），
  批间并发数 **≤3**，批内由服务端顺序处理
- 每轮同步先调一次 `/sync/attachment/query` 回查，服务端已有的照片直接跳过（零流量）

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
- 图片在**拍照保存时即压缩**：长边 **≤2000px**、JPEG 质量 **≈0.8**、目标单张 **≤1MB**；
  服务端原样存储不二次重编码，20MB 仅为兜底硬上限
- 用 query 回查 + `uploadUuid` 幂等重试消灭重复上传（响应丢失、超时、401 刷新后重试都安全）
- 弱网环境下降低批间并发数（3 → 1），批大小不变（失败重试粒度更细）
- 缩略图由服务端生成，App 不要自行制作/上传缩略图，省一倍上传量

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

### Q3: 图片上传成功但同步失败 / 预上传响应丢失怎么办？

**原因：** 附件预上传与数据同步是两个独立步骤，文件可能已在服务端但附件元数据未落库；
弱网下批量响应也可能丢失，App 不知道哪些照片已传成功。

**解决（v1.4 增强模式）：**
- **首选 query 回查**：用本地保存的 `uploadUuid` 调 `/sync/attachment/query`，
  `uploaded=true` 的项直接取回 `minioId/thumbMinioId`，不会产生任何重复流量。
- 也可以**带相同 `uploadUuid` 重放** batch/单文件请求：服务端返回 `status=exists` 与既有双 id，
  不会重复存储文件。
- 拿到 id 后带相同附件 `offlineUuid` 重发 `attachments[]` 即可（按 offlineUuid 幂等更新）。
- 若错误为"挂载主体反查失败"，先确保病害/桥梁/PART 已同步成功再重发附件（服务端回查云端，不要求同批）。
- 返回"服务端登记状态不一致，请联系管理员"说明服务端登记表与文件表被人为破坏，**不要换 uuid 硬传**，
  交后端排查。

**旧版兼容模式（未带 uploadUuid）：** 预上传不幂等，已缓存 `minioId` 时**不要重新上传文件**，
直接重发 `attachments[]`；本地未记录到结果（"缺少 minioId"）时只能重新预上传，会留下一条
孤立 `bi_file_map`（不影响业务，服务端后续按登记表/时间做 GC）。

### Q3b: 重拍/更换病害照片怎么处理？

附件 `offlineUuid` 保持不变，**为新文件生成新的 `uploadUuid`** 重新预上传，拿到新
`minioId/thumbMinioId` 后提交一次 `attachments[]`（同 offlineUuid、新 id），服务端更新既有行，
`attachment_count` 不翻倍。删除旧照片则提交 `offlineDeleted:1`（物理删除、count 自动重算）。

### Q3c: 新旧 App / 新旧服务端如何共存？

- 新 App + 新服务端：完整增强模式（幂等 + 批量 + 回查 + 服务端缩略图）。
- 旧 App + 新服务端：不带 `uploadUuid` 自动走旧路径，响应仍是裸 `minioId`，行为与升级前完全一致。
- 新 App + 旧服务端：旧服务端不认识 batch/query 端点（404），也会忽略单文件请求里的
  `uploadUuid` 字段并返回裸整数——**App 应把"端点 404 / 响应 data 是整数"作为旧服务端探测信号，
  自动回退到串行单文件 + 本地 minioId 缓存的旧逻辑**，升级服务端后再启用增强模式。

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
