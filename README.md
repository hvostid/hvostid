# HvostID

Distributed platform for responsible pet sales and transfers with a digital trust passport and owner-pet compatibility
scoring.

## Tech Stack

**Backend:**

- Java 25, Spring Boot 3.x, Spring Cloud Gateway
- Gradle multi-module (Kotlin DSL)
- PostgreSQL 16, MinIO (S3-compatible)
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
- Node.js 20+
- Docker and Docker Compose
- Gradle 8.x (or use the included wrapper)

### Quick Start

```bash
git clone https://github.com/hvostid/hvostid.git
cd hvostid
cp .env.example .env
```

### Local Development

Start infrastructure:

```bash
docker compose up -d postgres minio minio-init
```

Build and run backend:

```bash
./gradlew build
./gradlew :auth-service:bootRun
```

Run frontend:

```bash
cd frontend
npm install
npm run dev
```

Frontend dev server starts at http://localhost:3000 and proxies `/api` to Gateway at :8080.

### Run Everything with Docker Compose

```bash
./gradlew build
cd frontend && npm run build && cd ..
docker compose up --build
```

### SonarQube (optional)

```bash
docker compose --profile quality up -d sonarqube
./gradlew sonar -Dsonar.host.url=http://localhost:9090
```

## Project Structure

```
hvostid/
  settings.gradle.kts
  build.gradle.kts
  api-gateway/
  auth-service/
  listing-service/
  passport-service/
  matching-service/
  common/
  frontend/                -- React SPA
    src/
      api/                 -- axios client
      context/             -- AuthContext
      components/          -- shared components
      pages/               -- page components
  docker-compose.yml
  docker/
  k6/
  postman/
  .github/workflows/
```

## Git Workflow

- Main branch: `main`
- Feature branches: `feature/TXX-short-name`
- Merge via Pull Request with at least 1 reviewer
- CI runs automatically on every PR
- Release tags: `v1.0.0`, `v1.1.0`, etc.

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
