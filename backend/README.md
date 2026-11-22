# HighVibe backend

Spring Boot 4 microservices (Maven multi-module). See the [main README](../README.md) for the architecture
and [docs/HighVibe-Backend-API-Guide.pdf](../docs/HighVibe-Backend-API-Guide.pdf) for every API.

| Module | Port | Purpose |
| --- | --- | --- |
| `common-lib` | — | Shared DTOs, Kafka events, error format, JWT security auto-configuration |
| `discovery-service` | 8761 | Eureka registry |
| `config-service` | 8888 | Config server; per-service settings in `config-service/src/main/resources/config/` |
| `api-gateway` | 8080 | Routing, JWT check, CORS |
| `account-service` | 9010 | Auth, refresh tokens, plans, subscriptions, Stripe |
| `workspace-service` | 9020 | Projects, members, files (MinIO), live previews, Kafka consumer |
| `intelligence-service` | 9030 | AI chat (Gemini / demo), SSE streaming, usage, saga handler |

```bash
./mvnw verify                     # build + tests (Windows: .\mvnw.cmd verify)
./mvnw install -DskipTests        # build the jars only
docker build --build-arg MODULE=account-service -t highvibe/account-service .
```

Start order: discovery → config → account / workspace / intelligence → gateway
(`..\scripts\start-services.ps1` does this for you). Infrastructure comes from `../docker-compose.yml`.
