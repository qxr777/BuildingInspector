#!/usr/bin/env bash
# =============================================================================
# MinIO 对象清单导出（孤儿文件对账第 1 步）
#
# 输出 TSV 三列：object_name(对象名, 已去掉 xx/ 分片目录) \t size_bytes \t last_modified
# 配套：sql/tools/minio_orphan_audit.sql
#
# 用法：
#   # 方式 A：mc（任何能连通 MinIO 的机器，元数据最准）
#   ./minio_orphan_inventory.sh mc  local/public /tmp/minio_inventory.tsv
#
#   # 方式 B：MinIO 宿主机文件系统（单盘 FS 模式，对象原名落盘时可用）
#   ./minio_orphan_inventory.sh fs  /home/docker/data/minio/data/public /tmp/minio_inventory.tsv
#
# 说明：
# - 自动排除 .minio.sys 元数据目录与 orphan-quarantine/ 隔离前缀。
# - fs 模式时间取文件 mtime；若目录需要 root 权限，自行加 sudo 执行。
# =============================================================================
set -euo pipefail

MODE="${1:-}"
TARGET="${2:-}"
OUT="${3:-minio_inventory.tsv}"

if [ -z "$MODE" ] || [ -z "$TARGET" ]; then
  echo "用法: $0 <mc|fs> <alias/bucket 或 bucket根目录> [输出文件]" >&2
  exit 1
fi

case "$MODE" in
  mc)
    command -v mc  >/dev/null 2>&1 || { echo "未找到 mc（MinIO Client）" >&2; exit 1; }
    command -v jq  >/dev/null 2>&1 || { echo "未找到 jq" >&2; exit 1; }
    # mc ls --json 对前缀目录(无 lastModified)与对象都会输出行，过滤只留对象；
    # .key 形如 "57/5754....docx"，按本系统约定去掉首段两位分片目录即 objectName。
    mc ls --recursive --json "$TARGET" \
    | jq -r 'select(.lastModified != null)
            | select(.key | test("^orphan-quarantine/") | not)
            | [(.key | sub("^[^/]+/"; "")), (.size|tostring), .lastModified] | @tsv' \
    > "$OUT"
    ;;
  fs)
    [ -d "$TARGET" ] || { echo "目录不存在: $TARGET" >&2; exit 1; }
    if find "$TARGET" -maxdepth 0 -printf x >/dev/null 2>&1; then
      # GNU find（Linux MinIO 宿主机）：%f 取文件名即对象名；%TS 秒为小数，入库时截断。
      find "$TARGET" -type f \
        ! -path '*/.minio.sys/*' \
        ! -path '*/orphan-quarantine/*' \
        -printf '%f\t%s\t%TY-%Tm-%Td %TH:%TM:%TS\n' \
      > "$OUT"
    else
      # BSD find（macOS）：用 stat 输出 路径⇥字节⇥mtime，再把首列规整为 basename。
      find "$TARGET" -type f \
        ! -path '*/.minio.sys/*' \
        ! -path '*/orphan-quarantine/*' \
        -exec stat -f '%N%t%z%t%Sm' -t '%Y-%m-%d %H:%M:%S' {} + \
      | awk -F'\t' '{ n=$1; sub(/.*\//, "", n); print n "\t" $2 "\t" $3 }' \
      > "$OUT"
    fi
    ;;
  *)
    echo "未知模式: $MODE（应为 mc 或 fs）" >&2
    exit 1
    ;;
esac

printf '导出完成: %s，对象数 %s\n' "$OUT" "$(wc -l < "$OUT" | tr -d ' ')"
