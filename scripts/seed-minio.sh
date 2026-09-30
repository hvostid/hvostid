#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SEED_DATA_DIR="${ROOT_DIR}/scripts/seed-data"
MANIFEST="${SEED_DATA_DIR}/manifest.json"
PYTHON_BIN="${PYTHON_BIN:-python3}"

MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin}"
USE_DOCKER_MC=false

if [[ ! -f "${MANIFEST}" ]]; then
    echo "Error: manifest not found at ${MANIFEST}" >&2
    exit 1
fi

if command -v mc >/dev/null 2>&1; then
    MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://localhost:9000}"
else
    USE_DOCKER_MC=true
    docker compose build minio-init
    MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"
    MINIO_NETWORK="${MINIO_NETWORK:-$(docker inspect "$(docker compose ps -q minio)" --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null || echo hvostid_default)}"
fi

# Every Docker invocation gets its own --rm container, so any state set in
# one mc call (e.g. `mc alias set`) is gone in the next. Provide the alias
# via the MC_HOST_<name> env var so every container is preconfigured without
# needing a writable config volume.
MC_HOST_VALUE="http://${MINIO_ACCESS_KEY}:${MINIO_SECRET_KEY}@${MINIO_ENDPOINT#http://}"

run_mc() {
    if [[ "${USE_DOCKER_MC}" == false ]]; then
        MC_HOST_hvostid_seed="${MC_HOST_VALUE}" mc "$@"
        return
    fi
    if ! command -v docker >/dev/null 2>&1; then
        echo "Error: MinIO Client (mc) or Docker is required." >&2
        exit 1
    fi
    docker run --rm --network "${MINIO_NETWORK}" \
        -e "MC_HOST_hvostid_seed=${MC_HOST_VALUE}" \
        -v "${SEED_DATA_DIR}:/seed-data:ro" \
        --entrypoint mc hvostid/minio:security-2026-09-30 "$@"
}

mc_path_for_local_file() {
    local local_file="$1"
    if [[ "${USE_DOCKER_MC}" == false ]]; then
        echo "${SEED_DATA_DIR}/${local_file}"
    else
        echo "/seed-data/${local_file}"
    fi
}

echo "Waiting for MinIO at ${MINIO_ENDPOINT}..."
health_url="${MINIO_ENDPOINT}/minio/health/live"
if [[ "${USE_DOCKER_MC}" == true ]]; then
    health_url="http://minio:9000/minio/health/live"
fi
for _ in $(seq 1 30); do
    if curl -sf "${health_url}" >/dev/null 2>&1; then
        break
    fi
    if [[ "${USE_DOCKER_MC}" == true ]] && docker run --rm --network "${MINIO_NETWORK}" --entrypoint wget hvostid/minio:security-2026-09-30 -qO- "http://minio:9000/minio/health/live" >/dev/null 2>&1; then
        break
    fi
    sleep 2
done
if ! curl -sf "${health_url}" >/dev/null 2>&1 \
    && ! { [[ "${USE_DOCKER_MC}" == true ]] && docker run --rm --network "${MINIO_NETWORK}" --entrypoint wget hvostid/minio:security-2026-09-30 -qO- "http://minio:9000/minio/health/live" >/dev/null 2>&1; }; then
    echo "Error: MinIO is not ready at ${MINIO_ENDPOINT}" >&2
    exit 1
fi

manifest_entries="$("${PYTHON_BIN}" -c "import json,sys; [print(f\"{x['bucket']}\t{x['objectKey']}\t{x['localFile']}\") for x in json.load(sys.stdin)]" < "${MANIFEST}")"
manifest_entries="${manifest_entries//$'\r'/}"
[[ -n "$manifest_entries" ]] || { echo "Error: seed manifest is empty" >&2; exit 1; }
# Validate the complete fixture set before uploading any object.
while IFS=$'\t' read -r bucket object_key local_file; do
    [[ -f "${SEED_DATA_DIR}/${local_file}" ]] || { echo "Error: missing seed file ${SEED_DATA_DIR}/${local_file}" >&2; exit 1; }
done <<< "$manifest_entries"

count=0
while IFS=$'\t' read -r bucket object_key local_file; do
    source_path="$(mc_path_for_local_file "${local_file}")"
    run_mc mb --ignore-existing "hvostid_seed/${bucket}" >/dev/null
    run_mc cp "${source_path}" "hvostid_seed/${bucket}/${object_key}"
    count=$((count + 1))
done <<< "$manifest_entries"

echo "Uploaded ${count} objects to MinIO."
