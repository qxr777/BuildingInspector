# OSS 大包上传接入

大 ZIP 文件由平板直接上传 OSS，服务端只从 OSS 拉取流并解析，避免平板到业务服务器的长连接上传卡顿。

## 服务端配置

`ruoyi-admin/src/main/resources/application.yml` 中保存 OSS 的默认业务配置：

```yaml
oss:
  bridge-upload:
    enabled: true
    endpoint: https://oss-cn-beijing.aliyuncs.com
    internal-endpoint: https://oss-cn-beijing-internal.aliyuncs.com
    # 后端部署在阿里云北京地域时设为 true；本地或异地环境设为 false。
    use-internal-endpoint: true
    bucket-name: bi-upload-large
    # 为空时兼容既有的 1956_2026.zip 等根目录 objectName。
    object-prefix: ""
```

AccessKey 不能写入 `application.yml` 或 Git 仓库，由以下环境变量提供：

```text
OSS_ACCESS_KEY_ID=<服务端 RAM 用户 AccessKey ID>
OSS_ACCESS_KEY_SECRET=<服务端 RAM 用户 AccessKey Secret>
```

Docker Compose 会在部署机或 `.env` 中存在同名 `OSS_BRIDGE_UPLOAD_*` 变量时，用该变量覆盖上述 YAML；未设置时沿用 YAML 默认值。

公网和内网 Endpoint 会同时保留：

```text
# 公网：本地开发、平板直传、非同地域服务器使用
oss.bridge-upload.endpoint=https://oss-cn-beijing.aliyuncs.com

# 内网：仅阿里云北京同地域后端使用
oss.bridge-upload.internal-endpoint=https://oss-cn-beijing-internal.aliyuncs.com
oss.bridge-upload.use-internal-endpoint=true
```

平板直传始终使用公网 Endpoint；`use-internal-endpoint` 只影响后端从 OSS 下载和删除 ZIP 时使用的地址。后端不在阿里云北京同地域时必须保持 `false`。

内网 Endpoint 为：

```text
https://oss-cn-beijing-internal.aliyuncs.com
```

其他网络环境使用公网 Endpoint：

```text
https://oss-cn-beijing.aliyuncs.com
```

RAM 用户只需对该 Bucket 的临时上传目录授予 `oss:GetObject` 和 `oss:DeleteObject` 权限。AccessKey 只保留在部署环境中，不能放进 APK 或 Git 仓库。

使用仓库内的 Docker Compose 部署时，在部署机创建未提交的 `.env` 文件（或设置同名系统环境变量）：

```text
OSS_BRIDGE_UPLOAD_ENABLED=true
# 同时保留两套地址，通过开关选择后端实际使用的 Endpoint
OSS_BRIDGE_UPLOAD_ENDPOINT=https://oss-cn-beijing.aliyuncs.com
OSS_BRIDGE_UPLOAD_INTERNAL_ENDPOINT=https://oss-cn-beijing-internal.aliyuncs.com
OSS_BRIDGE_UPLOAD_USE_INTERNAL_ENDPOINT=true
OSS_BRIDGE_UPLOAD_BUCKET_NAME=bi-upload-large
OSS_BRIDGE_UPLOAD_OBJECT_PREFIX=
OSS_ACCESS_KEY_ID=<服务端 RAM 用户 AccessKey ID>
OSS_ACCESS_KEY_SECRET=<服务端 RAM 用户 AccessKey Secret>
```

## 平板回调接口

平板完成 OSS 上传后调用：

```http
POST /api/upload/bridgeData/oss
Content-Type: application/json
```

请求体：

```json
{
  "objectName": "bridge-upload/20260824/123_2026.zip"
}
```

配置了 `object-prefix` 时，`objectName` 必须以该前缀开头；最后一段文件名必须符合既有规则：

```text
buildingId.zip
buildingId_year.zip
```

接口的权限要求与 `/api/upload/bridgeData` 一致：`biz:disease:add`。

## 生命周期

1. 后端流式读取 OSS 对象并复用 `/api/upload/bridgeData` 的 ZIP 解析逻辑。
2. 解析完成后，后端只会在数据库事务**成功提交后**调用 OSS `DeleteObject` 删除 ZIP；不会出现事务回滚而 ZIP 已删除的情况。
3. 解析失败时不删除对象，方便修正后以同一 `objectName` 重试或排查。
4. 数据库已提交后若删除失败，接口数据不会回滚；程序会记录错误并保留 OSS 对象，供后续重试或清理，避免丢失已入库数据。
