# HvostID

Distributed platform for responsible pet sales and transfers with a digital trust passport and owner-pet compatibility
scoring.

## Tech Stack

**Backend:**

- Java 25, Spring Boot 4.0.x, Spring Cloud Gateway
- Gradle multi-module (Kotlin DSL)
- PostgreSQL 18, MinIO (S3-compatible)
- Docker + Docker Compose
- GitHub Actions (CI/CD)
- SonarQube, k6

**Frontend:**

- React 18, Vite, React Router 6
- Tailwind CSS, Axios

## Services

| Service          | Port | Description                                  |
|------------------|------|----------------------------------------------|
| Frontend         | 3000 | React SPA                                    |
| API Gateway      | 8080 | Routing, token validation, rate limiting     |
| Auth Service     | 8081 | Registration, login, opaque tokens, roles    |
| Listing Service  | 8082 | CRUD for pet listings, search, filters       |
| Passport Service | 8083 | Digital pet passport, documents, trust score |
| Matching Service | 8084 | Buyer questionnaire, compatibility score     |

## Getting Started

### Prerequisites

- Java 25
- Node.js 24+
- Docker and Docker Compose
- Gradle 9.x (or use the included wrapper)

### Quick Start

Start the entire platform (8 containers: 5 services + PostgreSQL + MinIO + frontend) with one command:

```bash
git clone https://github.com/hvostid/hvostid.git
cd hvostid
cp .env.example .env
docker compose up --build
```

Each backend service is built from source via a multi-stage Dockerfile, so no
local `./gradlew build` is required first. Subsequent `docker compose up
--build` runs reuse the Gradle dependency cache (BuildKit cache mount).

Once everything is healthy:

- Frontend: http://localhost:3000
- API Gateway: http://localhost:8080
- PostgreSQL: localhost:5432 (4 databases auto-created on first start: `hvostid_auth`, `hvostid_listing`, `hvostid_passport`, `hvostid_matching`)
- MinIO Console: http://localhost:9001 (bucket `pet-documents` auto-created on first start)

### Local Development

Start supporting infrastructure only:

```bash
docker compose up -d postgres minio minio-init
```

Build and run backend from your IDE or:

```bash
./gradlew :auth-service:bootRun
```

Run frontend:

```bash
cd frontend
npm install
npm run dev
```

Frontend dev server starts at http://localhost:3000 and proxies `/api` to Gateway at :8080.

### Hybrid Local Development

For day-to-day development, it is recommended to run all supporting infrastructure and non-active services via Docker
Compose, while starting the service currently being developed directly from the IDE or with Gradle.

This approach is especially useful for IntelliJ IDEA: when a Spring Boot service is started locally, Spring-specific IDE
features such as the Spring plugin, environment inspection, configuration assistance, and debugger integration work as
expected. If the service were also started inside Docker Compose, these capabilities would be limited or unavailable.

Example workflow:

```bash
docker compose up -d postgres minio minio-init listing-service passport-service matching-service api-gateway
./gradlew :auth-service:bootRun
```

### SonarQube (optional)

The `quality` Compose profile keeps SonarQube out of the default startup. Bring it up only when needed:

```bash
docker compose --profile quality up -d sonarqube
./gradlew sonar -Dsonar.host.url=http://localhost:9090 -Dsonar.token=squ_...
```

## Project Structure

```
hvostid/
  .github/workflows/
  api-gateway/
  auth-service/
  common/
  docker/
  frontend/                -- React SPA
    src/
      api/                 -- axios client
      context/             -- AuthContext
      components/          -- shared components
      pages/               -- page components
  k6/
  listing-service/
  matching-service/
  passport-service/
  postman/
  build.gradle.kts
  docker-compose.yml
  settings.gradle.kts
```

## Git Workflow

- Main branch: `main`
- Feature branches: `feature/TXX-short-name`
- Merge via Pull Request with at least 1 reviewer
- CI runs automatically on every PR
- Release tags: `v1.0.0`, `v1.1.0`, etc.

After cloning, install hook tooling once:

```bash
npm install                  # commitlint + husky at the repo root
npm install --prefix frontend  # eslint + prettier + lint-staged for the pre-commit hook
```

See [CONTRIBUTING.md](./CONTRIBUTING.md#local-enforcement) for hook
details, formatting commands (`./gradlew spotlessApply`,
`npm run lint:fix`), and the `--no-verify` escape hatch.

## API Documentation

Swagger UI (when services are running):

- Auth: http://localhost:8081/swagger-ui.html
- Listing: http://localhost:8082/swagger-ui.html
- Passport: http://localhost:8083/swagger-ui.html
- Matching: http://localhost:8084/swagger-ui.html

## Team

| # | Role                | Backend                 | Frontend                                                  |
|---|---------------------|-------------------------|-----------------------------------------------------------|
| 1 | Tech Lead / DevOps  | Gateway, CI/CD, Docker  | --                                                        |
| 2 | Auth/Profile        | Auth Service            | React scaffold, auth pages, seller pages, moderator panel |
| 3 | Catalog/Search      | Listing Service         | Catalog page, listing detail                              |
| 4 | Passport/Moderation | Passport Service        | Profile, matching result                                  |
| 5 | Matching + QA       | Matching Service, tests | --                                                        |

## License

MIT
