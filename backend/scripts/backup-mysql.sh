#!/usr/bin/env bash
# Daily MySQL backup to Aliyun OSS. Secrets are read from a protected env file,
# then passed to clients through private config/stdin rather than process args.
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
    echo "[backup] refusing env file not owned by current user or root" >&2
    exit 1
  fi
  if (( (8#${mode}) & 077 )); then
    echo "[backup] refusing env file with group/other permissions (expected 600)" >&2
    exit 1
  fi
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
}

require_single_line() {
  local name="$1" value="$2"
  if [ -z "${value}" ] || [[ "${value}" == *$'\n'* ]] || [[ "${value}" == *$'\r'* ]]; then
    echo "[backup] ${name} is missing or contains an invalid newline" >&2
    exit 1
  fi
}

mysql_client_config() {
  local escaped="${DB_PASS//\\/\\\\}"
  escaped="${escaped//\"/\\\"}"
  printf '[client]\nuser=%s\npassword="%s"\n' "${DB_USER}" "${escaped}"
}

write_oss_config() {
  printf '[Credentials]\nlanguage=EN\nendpoint=%s\naccessKeyID=%s\naccessKeySecret=%s\n' \
    "${OSS_ENDPOINT}" "${OSS_KEY_ID}" "${OSS_KEY_SECRET}" > "${OSS_CONFIG}"
  chmod 600 "${OSS_CONFIG}"
}

load_protected_env

DB_NAME="${MYSQL_DB:-career_db}"
DB_USER="${MYSQL_BACKUP_USER:-root}"
DB_PASS="${MYSQL_ROOT_PASSWORD:-}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-careerloop-mysql}"

# OSS_BUCKET is canonical; OSS_BUCKET_NAME is a deprecated compatibility fallback.
OSS_BUCKET="${OSS_BUCKET:-${OSS_BUCKET_NAME:-}}"
OSS_ENDPOINT="${OSS_ENDPOINT:-}"
OSS_KEY_ID="${OSS_ACCESS_KEY_ID:-}"
OSS_KEY_SECRET="${OSS_ACCESS_KEY_SECRET:-}"

require_single_line MYSQL_ROOT_PASSWORD "${DB_PASS}"
require_single_line OSS_BUCKET "${OSS_BUCKET}"
require_single_line OSS_ENDPOINT "${OSS_ENDPOINT}"
require_single_line OSS_ACCESS_KEY_ID "${OSS_KEY_ID}"
require_single_line OSS_ACCESS_KEY_SECRET "${OSS_KEY_SECRET}"
[[ "${DB_NAME}" =~ ^[A-Za-z0-9_]+$ ]] || { echo "[backup] invalid MYSQL_DB" >&2; exit 1; }
[[ "${MYSQL_CONTAINER}" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo "[backup] invalid MYSQL_CONTAINER" >&2; exit 1; }

# Do not let sourced/exported secrets leak into child-process environments.
export -n MYSQL_ROOT_PASSWORD OSS_ACCESS_KEY_ID OSS_ACCESS_KEY_SECRET \
  SERVER_CHAN_SEND_KEY BACKUP_NOTIFY_TOKEN 2>/dev/null || true

BACKUP_BASE="${CAREERLOOP_LOCAL_BACKUP_DIR:-${HOME}/.careerloop/backups}"
install -d -m 700 "${BACKUP_BASE}"
chmod 700 "${BACKUP_BASE}"
WORK_DIR="$(mktemp -d "${BACKUP_BASE}/.work.XXXXXX")"
chmod 700 "${WORK_DIR}"
trap 'rm -rf -- "${WORK_DIR}"' EXIT

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="${WORK_DIR}/careerloop_${TIMESTAMP}.sql.gz"
FINAL_LOCAL="${BACKUP_BASE}/careerloop_${TIMESTAMP}.sql.gz"
OSS_PATH="oss://${OSS_BUCKET}/backups/mysql/careerloop_${TIMESTAMP}.sql.gz"
OSS_CONFIG="${WORK_DIR}/ossutil.conf"
write_oss_config

echo "[backup] $(date '+%F %T') — starting MySQL backup"
mysql_client_config | docker exec -i "${MYSQL_CONTAINER}" sh -c '
  set -eu
  umask 077
  cfg="$(mktemp /tmp/careerloop-mysql.XXXXXX.cnf)"
  trap "rm -f -- \"$cfg\"" EXIT
  cat > "$cfg"
  mysqldump --defaults-extra-file="$cfg" --single-transaction --quick --hex-blob "$1"
' sh "${DB_NAME}" | gzip > "${BACKUP_FILE}"
chmod 600 "${BACKUP_FILE}"

DUMP_SIZE="$(du -sh "${BACKUP_FILE}" | cut -f1)"
echo "[backup] dump complete: ${DUMP_SIZE}"

if command -v ossutil >/dev/null 2>&1; then
  ossutil -c "${OSS_CONFIG}" cp "${BACKUP_FILE}" "${OSS_PATH}"
  echo "[backup] uploaded to ${OSS_PATH}"
else
  echo "[backup] WARNING: ossutil not found; retaining protected local backup" >&2
fi

mv "${BACKUP_FILE}" "${FINAL_LOCAL}"
chmod 600 "${FINAL_LOCAL}"
find "${BACKUP_BASE}" -maxdepth 1 -type f -name 'careerloop_*.sql.gz' -mtime +1 -delete

# ServerChan embeds its key in the URL, so it is intentionally not used.
# A generic notifier may instead send a bearer token in an HTTP header.
if [ -n "${BACKUP_NOTIFY_URL:-}" ] && [ -n "${BACKUP_NOTIFY_TOKEN:-}" ]; then
  [[ "${BACKUP_NOTIFY_URL}" =~ ^https://[^/?#]+(/[^?#]*)?$ ]] \
    || { echo "[backup] invalid BACKUP_NOTIFY_URL" >&2; exit 1; }
  require_single_line BACKUP_NOTIFY_TOKEN "${BACKUP_NOTIFY_TOKEN}"
  [[ "${BACKUP_NOTIFY_TOKEN}" =~ ^[A-Za-z0-9._~+/=-]+$ ]] \
    || { echo "[backup] invalid BACKUP_NOTIFY_TOKEN" >&2; exit 1; }
  {
    printf 'url = "%s"\n' "${BACKUP_NOTIFY_URL}"
    printf 'header = "Authorization: Bearer %s"\n' "${BACKUP_NOTIFY_TOKEN}"
    printf 'request = "POST"\n'
  } | curl --silent --show-error --fail --config - \
      --data-urlencode "title=[CareerLoop] 备份成功" \
      --data-urlencode "description=时间: $(date '+%F %T') 大小: ${DUMP_SIZE}"
fi

echo "[backup] $(date '+%F %T') — done; local copy ${FINAL_LOCAL}"
