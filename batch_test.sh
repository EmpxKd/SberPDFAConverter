#!/usr/bin/env bash
# Прогон каталога файлов через /api/v1/convert/batch одним запросом.
# Юзаж: ./batch_test.sh /путь/к/папке [http://localhost:8080]
set -euo pipefail

DIR="${1:-/home/aze/StprojectPDF/untitled/test-scans}"
HOST="${2:-http://localhost:8080}"

args=()
i=0
for f in "$DIR"/*; do
  [ -f "$f" ] || continue
  i=$((i+1))
  args+=(-F "page=@${f}" -F "docId=doc${i}")
done

echo "Файлов: $i"
resp=$(curl -s -X POST "$HOST/api/v1/convert/batch" "${args[@]}")
echo "$resp"

jobId=$(echo "$resp" | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)
echo "jobId=$jobId"

echo "Статус:  curl -s $HOST/api/v1/convert/batch/$jobId"
echo "Скачать: curl -s $HOST/api/v1/convert/batch/$jobId/result -o batch.zip"