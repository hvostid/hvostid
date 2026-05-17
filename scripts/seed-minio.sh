#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SEED_DATA_DIR="${ROOT_DIR}/scripts/seed-data"
MANIFEST="${SEED_DATA_DIR}/manifest.json"

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
    MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"
    MINIO_NETWORK="${MINIO_NETWORK:-$(docker inspect hvostid-minio --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null || echo hvostid_default)}"
fi

run_mc() {
    if [[ "${USE_DOCKER_MC}" == false ]]; then
        mc "$@"
        return
    fi
    if ! command -v docker >/dev/null 2>&1; then
        echo "Error: MinIO Client (mc) or Docker is required." >&2
        exit 1
    fi
    docker run --rm --network "${MINIO_NETWORK}" \
        -v "${SEED_DATA_DIR}:/seed-data:ro" \
        minio/mc "$@"
}

mc_path_for_local_file() {
    local local_file="$1"
    if [[ "${USE_DOCKER_MC}" == false ]]; then
        echo "${SEED_DATA_DIR}/${local_file}"
    else
        echo "/seed-data/${local_file}"
    fi
}

echo "Configuring MinIO alias..."
run_mc alias set hvostid-seed "${MINIO_ENDPOINT}" "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}" >/dev/null

echo "Waiting for MinIO at ${MINIO_ENDPOINT}..."
health_url="${MINIO_ENDPOINT}/minio/health/live"
if [[ "${USE_DOCKER_MC}" == true ]]; then
    health_url="http://minio:9000/minio/health/live"
fi
for _ in $(seq 1 30); do
    if curl -sf "${health_url}" >/dev/null 2>&1; then
        break
    fi
    if [[ "${USE_DOCKER_MC}" == true ]] && docker run --rm --network "${MINIO_NETWORK}" curlimages/curl:8.5.0 -sf "http://minio:9000/minio/health/live" >/dev/null 2>&1; then
        break
    fi
    sleep 2
done
if ! curl -sf "${health_url}" >/dev/null 2>&1 \
    && ! { [[ "${USE_DOCKER_MC}" == true ]] && docker run --rm --network "${MINIO_NETWORK}" curlimages/curl:8.5.0 -sf "http://minio:9000/minio/health/live" >/dev/null 2>&1; }; then
    echo "Error: MinIO is not ready at ${MINIO_ENDPOINT}" >&2
    exit 1
fi

count=0
# One python invocation extracts every (bucket, objectKey, localFile) tuple and
# emits them tab-separated, so we don't spawn three python processes per entry.
while IFS=$'\t' read -r bucket object_key local_file; do
    source_path="$(mc_path_for_local_file "${local_file}")"

    if [[ "${USE_DOCKER_MC}" == false ]] && [[ ! -f "${SEED_DATA_DIR}/${local_file}" ]]; then
        echo "Error: missing seed file ${SEED_DATA_DIR}/${local_file}" >&2
        exit 1
    fi

    run_mc mb --ignore-existing "hvostid-seed/${bucket}" >/dev/null 2>&1 || true
    run_mc cp "${source_path}" "hvostid-seed/${bucket}/${object_key}"
    count=$((count + 1))
done < <(python3 -c "import json; [print(f\"{x['bucket']}\t{x['objectKey']}\t{x['localFile']}\") for x in json.load(open('${MANIFEST}'))]")

echo "Uploaded ${count} objects to MinIO."
