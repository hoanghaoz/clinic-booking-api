#!/bin/sh
# Hàm dùng chung cho scripts/db/*.sh: bật 1 Postgres TẠM (container riêng, cổng riêng) để
# không đụng DB dev của docker compose. Container tự xoá khi script kết thúc (kể cả lỗi/Ctrl+C).
#
# Dùng bằng cách `. scripts/db/_temp-postgres.sh` rồi gọi `start_temp_postgres`.

SCHEMA_DB_PORT="${SCHEMA_DB_PORT:-55432}"
SCHEMA_DB_CONTAINER="clinic-schema-tmp-$$"
SCHEMA_DB_NAME="schema_tmp"

start_temp_postgres() {
  if ! docker info >/dev/null 2>&1; then
    echo "❌ Docker chưa chạy — khởi động Docker rồi chạy lại."
    exit 1
  fi

  trap 'docker rm -f "$SCHEMA_DB_CONTAINER" >/dev/null 2>&1 || true' EXIT INT TERM

  echo "▶ Bật Postgres tạm ở cổng $SCHEMA_DB_PORT (container $SCHEMA_DB_CONTAINER)..."
  docker run -d --rm --name "$SCHEMA_DB_CONTAINER" \
    -e POSTGRES_USER=clinic -e POSTGRES_PASSWORD=clinic -e POSTGRES_DB="$SCHEMA_DB_NAME" \
    -p "$SCHEMA_DB_PORT:5432" postgres:17-alpine >/dev/null

  i=0
  until docker exec "$SCHEMA_DB_CONTAINER" pg_isready -U clinic -d "$SCHEMA_DB_NAME" >/dev/null 2>&1; do
    i=$((i + 1))
    if [ "$i" -gt 30 ]; then
      echo "❌ Postgres tạm không lên sau 30s."
      exit 1
    fi
    sleep 1
  done
}

# Chạy SQL trên DB tạm, in kết quả dạng thô (không header).
temp_psql() {
  docker exec "$SCHEMA_DB_CONTAINER" psql -U clinic -d "$SCHEMA_DB_NAME" -tA -c "$1"
}

# Khởi động app 1 lần (không mở web server) trỏ vào DB tạm, với các tham số Spring truyền thêm.
# App KHÔNG tự thoát (có @Scheduled job giữ thread), nên chờ tới khi khởi động xong thì dừng nó.
# Trả về 0 nếu app khởi động thành công (Flyway + Hibernate không lỗi), 1 nếu app chết giữa chừng.
run_app_once() {
  log="$(mktemp)"
  DB_PORT="$SCHEMA_DB_PORT" DB_NAME="$SCHEMA_DB_NAME" ./gradlew bootRun -q \
    --args="--spring.main.web-application-type=none --logging.level.root=warn \
--logging.level.com.se100.clinic=info $*" >"$log" 2>&1 &
  app_pid=$!

  i=0
  while :; do
    if grep -q 'Started ClinicBookingApiApplication' "$log"; then
      kill "$app_pid" 2>/dev/null || true
      wait "$app_pid" 2>/dev/null || true
      rm -f "$log"
      return 0
    fi
    if ! kill -0 "$app_pid" 2>/dev/null; then
      grep -v -E '^WARNING|Sharing is only supported' "$log" | tail -40
      rm -f "$log"
      return 1
    fi
    i=$((i + 1))
    if [ "$i" -gt 300 ]; then
      kill "$app_pid" 2>/dev/null || true
      echo "❌ App không khởi động xong sau 300s."
      rm -f "$log"
      return 1
    fi
    sleep 1
  done
}
