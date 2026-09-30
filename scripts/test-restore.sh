#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
backup_dir="$root/tmp/restore-drill-$(date +%s)-$$"
# Run only in a disposable CI/dev source selected by COMPOSE_PROJECT_NAME.
[[ "${RESTORE_DRILL_DISPOSABLE:-}" == 'true' ]] || { echo 'Set RESTORE_DRILL_DISPOSABLE=true for a disposable source stack.' >&2; exit 1; }
docker compose exec -T postgres sh -ec 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d hvostid_matching -c "create table if not exists restore_probe (value text); insert into restore_probe values ('\''restore-ok'\'');"'
docker compose run --pull never --rm --no-deps --entrypoint sh minio-init -ec '
    mc alias set source http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null
    printf restore-object-ok | mc pipe "source/$MINIO_DOCUMENTS_BUCKET/restore-probe.txt"'
bash scripts/backup.sh "$backup_dir"
restore_project="hvostid-restore-drill-$$"
cleanup() {
    docker compose -p "$restore_project" -f docker-compose.restore.yml down -v >/dev/null
    docker compose exec -T postgres sh -ec 'psql -U "$POSTGRES_USER" -d hvostid_matching -c "drop table if exists restore_probe"' >/dev/null
    docker compose run --pull never --rm --no-deps --entrypoint sh minio-init -ec 'mc alias set source http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null; mc rm "source/$MINIO_DOCUMENTS_BUCKET/restore-probe.txt"' >/dev/null
}
trap cleanup EXIT
docker compose -p "$restore_project" -f docker-compose.restore.yml up -d --wait --wait-timeout 90 postgres minio
docker compose -p "$restore_project" -f docker-compose.restore.yml run --pull never --rm minio-init
COMPOSE_FILE=docker-compose.restore.yml COMPOSE_PROJECT_NAME="$restore_project" bash scripts/restore.sh "$backup_dir" --empty-target
restored=$(docker compose -p "$restore_project" -f docker-compose.restore.yml exec -T postgres sh -ec 'psql -U "$POSTGRES_USER" -d hvostid_matching -Atc "select value from restore_probe limit 1"')
restored="${restored//$'\r'/}"
[[ "$restored" == 'restore-ok' ]]
restored_object=$(docker compose -p "$restore_project" -f docker-compose.restore.yml run --pull never --rm --no-deps --entrypoint sh minio-init -ec 'mc alias set target http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null; mc cat "target/$MINIO_DOCUMENTS_BUCKET/restore-probe.txt"')
restored_object="${restored_object//$'\r'/}"
[[ "$restored_object" == 'restore-object-ok' ]]
echo 'Isolated PostgreSQL + object restore drill passed.'
