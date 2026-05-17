#!/usr/bin/env bash
# Run all Postman collections via Newman against the local docker compose stack.
#
# Usage:
#   ./postman/run-newman.sh                  # run every COL-*.json
#   ./postman/run-newman.sh COL-01-public-api  # run a single collection by short name
#   BASE_URL=http://localhost:8081 ./postman/run-newman.sh
#
# Requires Newman (https://github.com/postmanlabs/newman):
#   npm install -g newman
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${HERE}/hvostid-local.postman_environment.json"

if ! command -v newman >/dev/null 2>&1; then
    echo "newman is not installed. Install it with: npm install -g newman" >&2
    exit 127
fi

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "Missing environment file: ${ENV_FILE}" >&2
    exit 1
fi

# Pick collections. With no args run them all in order.
if [[ $# -gt 0 ]]; then
    COLLECTIONS=()
    for name in "$@"; do
        if [[ -f "${HERE}/${name}.json" ]]; then
            COLLECTIONS+=("${HERE}/${name}.json")
        elif [[ -f "${HERE}/${name}" ]]; then
            COLLECTIONS+=("${HERE}/${name}")
        else
            echo "Collection not found: ${name}" >&2
            exit 1
        fi
    done
else
    mapfile -t COLLECTIONS < <(ls "${HERE}"/COL-*.json | sort)
fi

# Optional override for the gateway URL without editing the env file.
ENV_OVERRIDES=()
if [[ -n "${BASE_URL:-}" ]]; then
    ENV_OVERRIDES+=("--env-var" "baseUrl=${BASE_URL}")
fi

FAILED=0
for collection in "${COLLECTIONS[@]}"; do
    echo "=== Running $(basename "${collection}") ==="
    if ! newman run "${collection}" \
            --environment "${ENV_FILE}" \
            --reporters cli,json \
            --reporter-json-export "${HERE}/.newman-$(basename "${collection}" .json).json" \
            "${ENV_OVERRIDES[@]}"; then
        FAILED=$((FAILED + 1))
    fi
done

if [[ ${FAILED} -gt 0 ]]; then
    echo "${FAILED} collection(s) failed" >&2
    exit 1
fi

echo "All collections passed"
