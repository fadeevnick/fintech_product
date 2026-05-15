#!/usr/bin/env bash
set -euo pipefail

declare -A shells=(
  [enduser]="http://localhost:3000"
  [merchant]="http://localhost:3001"
  [backoffice]="http://localhost:3002"
)

for shell in "${!shells[@]}"; do
  curl -fsS "${shells[$shell]}" | grep -q '<div id="root"></div>'
  echo "UI-01 $shell spa shell pass"
done
