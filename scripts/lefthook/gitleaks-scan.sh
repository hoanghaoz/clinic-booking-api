#!/bin/sh
# Quét secret trong các thay đổi ĐÃ staged bằng gitleaks, chạy qua Docker image chính thức
# (không cần cài gitleaks binary riêng — xem docs/setup/DECISIONS.md #9).
#
# Nếu Docker daemon không chạy: CẢNH BÁO và BỎ QUA (không chặn commit) — đúng nguyên tắc
# "tooling phải giúp, không được chặn". Nếu Docker chạy VÀ gitleaks tìm thấy secret: CHẶN
# commit — đây là ngoại lệ DUY NHẤT được phép chặn ở pre-commit.
set -u

if ! docker info >/dev/null 2>&1; then
  echo "⚠️  Docker chưa chạy — bỏ qua quét gitleaks lần commit này."
  echo "   Khởi động Docker rồi commit lại để được quét secret đầy đủ."
  exit 0
fi

docker run --rm -v "$(pwd):/repo" -w /repo zricethezav/gitleaks:v8.30.1 \
  protect --staged --source /repo --redact -v
STATUS=$?

if [ "$STATUS" -ne 0 ]; then
  echo "❌ gitleaks phát hiện có thể có secret trong thay đổi đang commit — xem log ở trên."
  echo "   Gỡ secret khỏi code (dùng biến môi trường thay vì hardcode) rồi commit lại."
fi

exit "$STATUS"
