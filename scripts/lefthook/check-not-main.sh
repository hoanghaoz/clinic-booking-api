#!/bin/sh
# Chặn push thẳng vào nhánh main — phải mở Pull Request.
set -eu

BRANCH="$(git rev-parse --abbrev-ref HEAD)"

if [ "$BRANCH" = "main" ]; then
  echo "❌ Không được push thẳng vào nhánh 'main'. Hãy tạo branch riêng rồi mở Pull Request."
  echo "   Quy ước đặt tên branch: <type>/<module>/<mô-tả-ngắn> (vd: feat/booking/waitlist)"
  exit 1
fi

echo "✅ Đang ở branch '$BRANCH', không phải main — cho phép push."
