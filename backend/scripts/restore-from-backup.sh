#!/usr/bin/env bash
# Restore MySQL from Aliyun OSS without exposing credentials in process args.
set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${CAREERLOOP_ENV_FILE:-${SCRIPT_DIR}/../.env.prod}"

load_protected_env() {
  [ -f "${ENV_FILE}" ] || return 0
  local owner mode
  owner="$(stat -c '%u' "${ENV_FILE}")"
  mode="$(stat -c '%a' "${ENV_FILE}")"
  if [ "${owner}" != "$(id -u)" ] && [ "${owner}" != "0" ]; then
    echo "[restore] refusing env file not owned by current user or root" >&2
    exit 1
  fi
  if (( (8#${mode}) & 077 )); then
    echo "[restore] refusing env file with group/other permissions (expected 600)" >&2
    exit 1
  fi
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
}

require_single_line() {
  local name="$1" value="$2"
  if [ -z "${value}" ] || [[ "${value}" == *$'\n'* ]] || [[ "${value}" == *$'\r'* ]]; then
    echo "[restore] ${name} is missing or contains an invalid newline" >&2
    exit 1
  fi
}

mysql_client_config() {
  local escaped="${DB_PASS//\\/\\\\}"
  escaped="${escaped//\"/\\\"}"
  printf '[client]\nuser=%s\npassword="%s"\n' "${DB_USER}" "${escaped}"
}

load_protected_env

DB_NAME="${MYSQL_DB:-career_db}"
DB_USER="${MYSQL_BACKUP_USER:-root}"
DB_PASS="${MYSQL_ROOT_PASSWORD:-}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-careerloop-mysql}"
OSS_BUCKET="${OSS_BUCKET:-${OSS_BUCKET_NAME:-}}"
OSS_ENDPOINT="${OSS_ENDPOINT:-}"
OSS_KEY_ID="${OSS_ACCESS_KEY_ID:-}"
OSS_KEY_SECRET="${OSS_ACCESS_KEY_SECRET:-}"

require_single_line MYSQL_ROOT_PASSWORD "${DB_PASS}"
require_single_line OSS_BUCKET "${OSS_BUCKET}"
require_single_line OSS_ENDPOINT "${OSS_ENDPOINT}"
require_single_line OSS_ACCESS_KEY_ID "${OSS_KEY_ID}"
require_single_line OSS_ACCESS_KEY_SECRET "${OSS_KEY_SECRET}"
[[ "${DB_NAME}" =~ ^[A-Za-z0-9_]+$ ]] || { echo "[restore] invalid MYSQL_DB" >&2; exit 1; }
[[ "${MYSQL_CONTAINER}" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo "[restore] invalid MYSQL_CONTAINER" >&2; exit 1; }
export -n MYSQL_ROOT_PASSWORD OSS_ACCESS_KEY_ID OSS_ACCESS_KEY_SECRET 2>/dev/null || true

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/careerloop-restore.XXXXXX")"
chmod 700 "${WORK_DIR}"
trap 'rm -rf -- "${WORK_DIR}"' EXIT
OSS_CONFIG="${WORK_DIR}/ossutil.conf"
printf '[Credentials]\nlanguage=EN\nendpoint=%s\naccessKeyID=%s\naccessKeySecret=%s\n' \
  "${OSS_ENDPOINT}" "${OSS_KEY_ID}" "${OSS_KEY_SECRET}" > "${OSS_CONFIG}"
chmod 600 "${OSS_CONFIG}"

BACKUP_NAME="${1:-}"
echo "CareerLoop MySQL Restore"

if [ -z "${BACKUP_NAME}" ]; then
  echo "Available backups:"
  ossutil -c "${OSS_CONFIG}" ls "oss://${OSS_BUCKET}/backups/mysql/" \
    | awk '/\.sql\.gz$/ {print $NF}'
  read -rp "Enter backup filename: " BACKUP_NAME
fi

[[ "${BACKUP_NAME}" =~ ^careerloop_[0-9]{8}_[0-9]{6}\.sql\.gz$ ]] \
  || { echo "[restore] invalid backup filename" >&2; exit 1; }

LOCAL_DUMP="${WORK_DIR}/${BACKUP_NAME}"
OSS_PATH="oss://${OSS_BUCKET}/backups/mysql/${BACKUP_NAME}"
echo "[restore] downloading ${BACKUP_NAME}"
ossutil -c "${OSS_CONFIG}" cp "${OSS_PATH}" "${LOCAL_DUMP}"
chmod 600 "${LOCAL_DUMP}"
gzip -t "${LOCAL_DUMP}"

echo "WARNING: this replaces data in '${DB_NAME}'."
read -rp "Type 'YES' to confirm: " CONFIRM
[ "${CONFIRM}" = "YES" ] || { echo "Aborted."; exit 1; }

# A marker separates the private client config from the SQL stream. The
# container writes the config mode 600, consumes it locally, then removes it.
{
  mysql_client_config
  printf '%s\n' '__CAREERLOOP_SQL_STREAM__'
  gzip -dc "${LOCAL_DUMP}"
} | docker exec -i "${MYSQL_CONTAINER}" sh -c '
  set -eu
  umask 077
  cfg="$(mktemp /tmp/careerloop-mysql.XXXXXX.cnf)"
  trap "rm -f -- \"$cfg\"" EXIT
  while IFS= read -r line; do
    [ "$line" = "__CAREERLOOP_SQL_STREAM__" ] && break
    printf "%s\n" "$line" >> "$cfg"
  done
  mysql --defaults-extra-file="$cfg" "$1"
' sh "${DB_NAME}"

echo "[restore] restore complete; verifying row counts"
mysql_client_config | docker exec -i "${MYSQL_CONTAINER}" sh -c '
  set -eu
  umask 077
  cfg="$(mktemp /tmp/careerloop-mysql.XXXXXX.cnf)"
  trap "rm -f -- \"$cfg\"" EXIT
  cat > "$cfg"
  mysql --defaults-extra-file="$cfg" "$1" \
    -e "SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_rows DESC LIMIT 15;"
' sh "${DB_NAME}"

echo "[restore] done"
