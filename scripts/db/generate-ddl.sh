#!/bin/sh
# Sinh DDL (create table/index/FK) từ ENTITY hiện tại bằng Hibernate — để KHÔNG phải viết tay
# SQL khi thêm bảng/cột. Kết quả chỉ là bản nháp: copy phần cần thiết sang file migration mới
# rồi chỉnh theo docs/conventions/database-migrations.md (mục 5).
#
# Dùng: scripts/db/generate-ddl.sh [file-output]   (mặc định build/schema/generated-ddl.sql)
# Cần Docker. KHÔNG đụng DB dev (dùng Postgres tạm, cổng $SCHEMA_DB_PORT, mặc định 55432).
set -eu

cd "$(dirname "$0")/../.."
. scripts/db/_temp-postgres.sh

OUT="${1:-build/schema/generated-ddl.sql}"
mkdir -p "$(dirname "$OUT")"
OUT_ABS="$(cd "$(dirname "$OUT")" && pwd)/$(basename "$OUT")"
rm -f "$OUT_ABS"

start_temp_postgres

echo "▶ Hibernate đang sinh DDL từ entity..."
if ! run_app_once \
  --spring.flyway.enabled=false \
  --spring.jpa.hibernate.ddl-auto=none \
  --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create \
  "--spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=$OUT_ABS" \
  "--spring.jpa.properties.hibernate.hbm2ddl.delimiter=;" \
  --spring.jpa.properties.hibernate.format_sql=true ||
  [ ! -s "$OUT_ABS" ]; then
  echo "❌ Không sinh được DDL — xem lỗi phía trên (thường là mapping entity sai)."
  exit 1
fi

echo "✅ Đã sinh $(grep -c 'create table' "$OUT_ABS") bảng vào $OUT"
echo "   Copy phần bảng/cột bạn vừa đổi sang file migration MỚI, đừng copy cả file."
