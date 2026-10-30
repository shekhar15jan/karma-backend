# Karma OS API

Spring Boot 3.3 / Java 21 backend for Karma OS. Companion repos: `karma-frontend` (Angular UI) and `karma-renderer` (standalone visual/video rendering service).

## Run locally

Requirements: Java 21, Maven, PostgreSQL 14+.

```bash
# 1. database (once)
psql -U postgres -c "CREATE ROLE karma_os LOGIN PASSWORD 'karma_os_dev';" -c "CREATE DATABASE karma_os OWNER karma_os;"

# 2. run (dev profile is the default; Flyway applies all migrations on startup)
mvn spring-boot:run
```

API: `http://localhost:8000/api/v1` · health: `/actuator/health` · Swagger: `/swagger-ui.html`

The "Bypass (Local Developer Mode)" button in the UI calls `/api/v1/auth/dev-bypass`, which only exists when `app.dev-bypass.enabled=true` (set in the `dev` profile). It returns 404 under any other profile.

## Configuration (environment variables)

| Variable | Default | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | **Set to `prod` when deploying** (disables dev-bypass) |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `localhost` / `5432` / `karma_os` / `karma_os` / `karma_os_dev` | |
| `JWT_SECRET` | dev placeholder | Must be changed outside local dev |
| `PROVIDER_KEY_SECRET` | dev placeholder | Encrypts stored AI-provider API keys; changing it makes stored keys unreadable |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | |

AI provider keys (OpenAI, Anthropic, Gemini, …) are added in the UI under **AI Providers**; no LLM calls work until at least one is configured.

## Docker

`docker build -t karma-api .` (exposes 8000; includes ffmpeg and `edge-tts` for voice/video). The frontend's nginx proxies `/api`, `/ws` and `/sse` to a host named `karma-api:8000`.

## Notes

- The API does not call `karma-renderer`; video assembly uses the built-in FFmpeg provider.
- Schema changes go in new `V<n>__*.sql` Flyway migrations. `ddl-auto` is `none`; to check entities against the schema run once with `--spring.jpa.hibernate.ddl-auto=validate`.
