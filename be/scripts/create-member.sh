#!/usr/bin/env bash
set -euo pipefail

# Usage: ./scripts/create-member.sh <loginId> <password>
# MySQL 접속 정보는 .env 파일 또는 환경변수에서 읽는다.

if [ $# -ne 2 ]; then
  echo "Usage: $0 <loginId> <password>"
  exit 1
fi

LOGIN_ID="$1"
PASSWORD="$2"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/../.env"

if [ -f "$ENV_FILE" ]; then
  set -a
  source "$ENV_FILE"
  set +a
fi

DB_HOST="${MYSQL_HOST%%:*}"
DB_PORT="${MYSQL_HOST#*:}"
DB_PORT="${DB_PORT%%/*}"
DB_NAME="${MYSQL_HOST##*/}"
DB_USER="${MYSQL_USERNAME}"
DB_PASS="${MYSQL_PASSWORD}"

HASHED=$(python3 -c "
import bcrypt
print(bcrypt.hashpw('${PASSWORD}'.encode(), bcrypt.gensalt()).decode())
")

mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" -e \
  "INSERT INTO member (login_id, password, created_at, updated_at) VALUES ('${LOGIN_ID}', '${HASHED}', NOW(), NOW());"

echo "계정 생성 완료: $LOGIN_ID"
