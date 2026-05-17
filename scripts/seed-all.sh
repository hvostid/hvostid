#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

WIPE_VOLUMES=true
for arg in "$@"; do
    case "${arg}" in
        --no-wipe)
            WIPE_VOLUMES=false
            ;;
        -h|--help)
            echo "Usage: $0 [--no-wipe]"
            echo "  --no-wipe  Reuse existing Docker volumes (skip docker compose down -v)"
            exit 0
            ;;
        *)
            echo "Unknown option: ${arg}" >&2
            exit 1
            ;;
    esac
done

export SPRING_PROFILES_ACTIVE=demo

if [[ -f .env ]]; then
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
fi

echo "==> HvostID demo seed (SPRING_PROFILES_ACTIVE=demo)"

if [[ "${WIPE_VOLUMES}" == true ]]; then
    echo "==> Stopping stack and removing volumes..."
    docker compose down -v
else
    echo "==> Stopping stack (keeping volumes)..."
    docker compose down
fi

echo "==> Starting stack (build)..."
docker compose up -d --build

echo "==> Waiting for core services to become healthy..."
wait_for_url() {
    local name="$1"
    local url="$2"
    local attempts="${3:-60}"
    for ((i = 1; i <= attempts; i++)); do
        if curl -sf "${url}" >/dev/null 2>&1; then
            echo "  ${name} is up"
            return 0
        fi
        sleep 5
    done
    echo "Error: ${name} did not become healthy (${url})" >&2
    return 1
}

wait_for_url "auth-service" "http://localhost:8081/actuator/health"
wait_for_url "listing-service" "http://localhost:8082/actuator/health"
wait_for_url "passport-service" "http://localhost:8083/actuator/health"
wait_for_url "matching-service" "http://localhost:8084/actuator/health"
wait_for_url "api-gateway" "http://localhost:8080/actuator/health"

echo "==> Uploading sample files to MinIO..."
"${ROOT_DIR}/scripts/seed-minio.sh"

echo ""
echo "==> Demo credentials (password for all: demo1234)"
echo ""
printf "%-30s %-20s\n" "EMAIL" "ROLES"
printf "%-30s %-20s\n" "-----" "-----"
printf "%-30s %-20s\n" "admin@demo.hvostid" "ADMIN"
printf "%-30s %-20s\n" "moderator@demo.hvostid" "MODERATOR"
printf "%-30s %-20s\n" "seller1@demo.hvostid" "SELLER"
printf "%-30s %-20s\n" "seller2@demo.hvostid" "SELLER"
printf "%-30s %-20s\n" "seller3@demo.hvostid" "SELLER"
printf "%-30s %-20s\n" "seller4@demo.hvostid" "SELLER"
printf "%-30s %-20s\n" "seller5@demo.hvostid" "SELLER"
printf "%-30s %-20s\n" "seller6@demo.hvostid" "SELLER"
printf "%-30s %-20s\n" "buyer1@demo.hvostid" "BUYER"
printf "%-30s %-20s\n" "buyer2@demo.hvostid" "BUYER"
printf "%-30s %-20s\n" "buyer3@demo.hvostid" "BUYER"
printf "%-30s %-20s\n" "buyer4@demo.hvostid" "BUYER"
printf "%-30s %-20s\n" "buyer5@demo.hvostid" "BUYER"
printf "%-30s %-20s\n" "buyer6@demo.hvostid" "BUYER"
echo ""
echo "Quick check:"
echo "  curl -s -X POST http://localhost:8080/api/v1/auth/login \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"email\":\"buyer1@demo.hvostid\",\"password\":\"demo1234\"}'"
echo ""
echo "Frontend: http://localhost:3000  |  API Gateway: http://localhost:8080"
echo "Done."
