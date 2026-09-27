#!/bin/sh
# Tạo 1 label GitHub cho mỗi module, dùng để gắn vào Issue/PR.
#
# ⚠️  Script GHI lên GitHub (tạo label) — người có quyền admin repo chạy tay 1 lần sau khi review.
#
# Cách chạy (cần cài GitHub CLI: https://cli.github.com, và `gh auth login` trước):
#   chmod +x scripts/create-labels.sh
#   ./scripts/create-labels.sh
set -eu

MODULES="identity facility catalog doctor schedule patient booking reception examination billing violation corporate notification reporting shared"

for module in $MODULES; do
  gh label create "module:${module}" --color "0E8A16" --description "Module ${module}" --force
done

gh label create "build/ci" --color "5319E7" --description "Hạ tầng, build, CI/CD" --force

echo "✅ Đã tạo xong label cho ${MODULES} và build/ci."
