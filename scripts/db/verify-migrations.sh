#!/bin/sh
# Kiểm tra migration trước khi mở PR: chạy TOÀN BỘ migration trên 1 DB trống, rồi bật
# Hibernate `ddl-auto=validate` để so entity với bảng. Lỗi = entity và migration lệch nhau
# (vd. thêm field trong entity mà quên viết migration).
#
# Dùng: scripts/db/verify-migrations.sh
# Cần Docker. KHÔNG đụng DB dev (dùng Postgres tạm, cổng $SCHEMA_DB_PORT, mặc định 55432).
set -eu

cd "$(dirname "$0")/../.."
. scripts/db/_temp-postgres.sh

start_temp_postgres

echo "▶ Chạy migration + validate entity..."
if ! run_app_once --spring.jpa.hibernate.ddl-auto=validate; then
  echo "❌ Migration lỗi hoặc entity không khớp bảng — xem lỗi phía trên."
  exit 1
fi

APPLIED="$(temp_psql "select count(*) from flyway_schema_history where success")"
TABLES="$(temp_psql "select count(*) from information_schema.tables where table_schema = 'public' and table_name <> 'flyway_schema_history'")"
FKS="$(temp_psql "select count(*) from information_schema.table_constraints where constraint_type = 'FOREIGN KEY'")"

echo "✅ OK: $APPLIED migration, $TABLES bảng, $FKS foreign key — entity khớp schema."
