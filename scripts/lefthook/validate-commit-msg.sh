#!/bin/sh
# Kiểm tra commit message theo Conventional Commits.
# Lefthook gọi: ./scripts/lefthook/validate-commit-msg.sh <đường-dẫn-file-commit-msg>
set -eu

MSG_FILE="$1"
FIRST_LINE="$(head -n 1 "$MSG_FILE")"

# type(scope): description   |   type: description
# type hợp lệ: feat fix docs style refactor perf test build ci chore revert
PATTERN='^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-z0-9-]+\))?: .+$'

if ! echo "$FIRST_LINE" | grep -Eq "$PATTERN"; then
  echo "❌ Commit message không đúng định dạng Conventional Commits."
  echo ""
  echo "   Dòng đầu hiện tại: $FIRST_LINE"
  echo ""
  echo "   Đúng định dạng:    <type>(<scope>): <mô tả ngắn>"
  echo "   Ví dụ:             feat(booking): thêm chức năng waitlist"
  echo "                      fix(doctor): sửa lỗi trùng mã chuyên khoa"
  echo "                      docs(setup): cập nhật README"
  echo "                      build: nâng cấp Spring Boot lên 4.1.1"
  echo ""
  echo "   type hợp lệ: feat fix docs style refactor perf test build ci chore revert"
  exit 1
fi

TYPE="$(echo "$FIRST_LINE" | sed -E 's/^([a-z]+)(\(.+\))?:.*/\1/')"
SCOPE="$(echo "$FIRST_LINE" | sed -nE 's/^[a-z]+\(([a-z0-9-]+)\):.*/\1/p')"

if { [ "$TYPE" = "feat" ] || [ "$TYPE" = "fix" ]; } && [ -z "$SCOPE" ]; then
  echo "❌ Commit type '$TYPE' BẮT BUỘC phải có scope là tên module."
  echo ""
  echo "   Ví dụ đúng: ${TYPE}(booking): thêm chức năng waitlist"
  echo "   Danh sách module: identity facility catalog doctor schedule patient"
  echo "                      booking reception examination billing violation"
  echo "                      corporate notification reporting shared"
  exit 1
fi

echo "✅ Commit message hợp lệ."
