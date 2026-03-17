# HvostID

Distributed platform for responsible pet sales and transfers with a digital trust passport and owner-pet compatibility scoring.

## Tech Stack

- **Language:** Java 25
- **Framework:** Spring Boot 3.x, Spring Cloud Gateway
- **Build:** Gradle multi-module (Kotlin DSL)
- **Database:** PostgreSQL 16
- **File Storage:** MinIO (S3-compatible)
- **Containers:** Docker + Docker Compose
- **CI/CD:** GitHub Actions
- **Code Quality:** SonarQube
- **Load Testing:** k6

## Microservices

| Service | Port | Description |
|---------|------|-------------|
| API Gateway | 8080 | Routing, token validation, rate limiting |
| Auth Service | 8081 | Registration, login, opaque tokens, roles |
| Listing Service | 8082 | CRUD for pet listings, search, filters |
| Passport Service | 8083 | Digital pet passport, documents, trust score |
| Matching Service | 8084 | Buyer questionnaire, compatibility score |

## Getting Started

### Prerequisites

- Java 25
- Docker and Docker Compose
- Gradle 8.x (or use the included wrapper)

### Local Development

1. Clone the repository:
```bash
git clone https://github.com/<your-org>/hvostid.git
cd hvostid
```

2. Copy the environment file:
```bash
cp .env.example .env
```

3. Start infrastructure (PostgreSQL + MinIO):
```bash
docker compose up -d postgres minio
```

4. Build all services:
```bash
./gradlew build
```

5. Run a specific service:
```bash
./gradlew :auth-service:bootRun
```

### Run All Services with Docker Compose

```bash
./gradlew build
docker compose up --build
```

### SonarQube (optional)

```bash
docker compose --profile quality up -d sonarqube
# Wait for SonarQube to start, then:
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
  docker-compose.yml
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

API documentation is available via Swagger UI when services are running:
- Auth Service: http://localhost:8081/swagger-ui.html
- Listing Service: http://localhost:8082/swagger-ui.html
- Passport Service: http://localhost:8083/swagger-ui.html
- Matching Service: http://localhost:8084/swagger-ui.html

## Team

| # | Role | Responsibility |
|---|------|----------------|
| 1 | Tech Lead / DevOps | Gateway, CI/CD, Docker |
| 2 | Auth/Profile | Auth Service, tokens, security |
| 3 | Catalog/Search | Listing Service |
| 4 | Passport/Moderation | Passport Service, MinIO |
| 5 | Matching + QA | Matching Service, tests, load testing |
