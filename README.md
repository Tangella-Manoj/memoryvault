# MemoryVault

![CI](https://github.com/OWNER/memoryvault/actions/workflows/ci.yml/badge.svg)

People save hundreds of articles, videos, and tweets they mean to revisit — and almost never do. MemoryVault is an intelligence layer over your saved links: it detects what each item is about, understands the emotional and life context you saved it in, and proactively resurfaces forgotten items when they're actually relevant again, instead of leaving them to rot in a bookmarks folder.

## Architecture

```
┌───────────┐      HTTPS/JSON       ┌──────────────┐     JDBC      ┌────────────┐
│  React     │ ───────────────────▶ │  Spring Boot  │ ────────────▶ │ MySQL 8     │
│  (Vite)    │ ◀─────────────────── │  REST API     │ ◀──────────── │ (Docker)    │
└───────────┘                       │               │               └────────────┘
      ▲                             │  @Async ──────┼──▶ Claude API (summarize,
      │ chrome.runtime               │  intelligence  │    tag, context-classify)
      │                             │  pipeline      │
┌───────────┐   chrome-extension://  │               │
│  Chrome    │ ───────────────────▶ │  X-Chrome-Token│
│  extension │   session token       │  auth          │
└───────────┘                       └──────┬─────────┘
                                            │
                              GitHub Actions│ push → main
                       ┌────────────────────┴───────────────────┐
                       │ mvnw test (H2) → npm build → docker build │
                       └──────────────────────────────────────────┘
```

**Environment isolation**: Java 17 + Maven via SDKMAN (`backend/.sdkmanrc`) with Maven Wrapper as the real guarantee (`./mvnw` works with no SDKMAN installed). Node 20 via nvm (`frontend/.nvmrc`). MySQL runs only in Docker — no system MySQL is ever touched.

## Setup (fresh machine)

```bash
git clone <repo-url> memoryvault && cd memoryvault
cp .env.example .env               # fill in ANTHROPIC_API_KEY at minimum
docker compose up -d               # MySQL + phpMyAdmin
docker exec -i memoryvault_mysql mysql -u"$(grep MYSQL_USER .env|cut -d= -f2)" -p"$(grep MYSQL_PASSWORD .env|cut -d= -f2)" "$(grep MYSQL_DATABASE .env|cut -d= -f2)" < sql/schema.sql
docker exec -i memoryvault_mysql mysql -u"$(grep MYSQL_USER .env|cut -d= -f2)" -p"$(grep MYSQL_PASSWORD .env|cut -d= -f2)" "$(grep MYSQL_DATABASE .env|cut -d= -f2)" < sql/migration_001_refresh_tokens.sql
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev &
cd ../frontend && nvm use && npm install && npm run dev
```

Backend: `http://localhost:8091` (port configurable via `SERVER_PORT` in `.env`). Frontend: `http://localhost:5173`.

## Load the Chrome extension

1. `chrome://extensions` → enable Developer mode
2. "Load unpacked" → select the `extension/` folder
3. Click the MemoryVault icon → log in with your app credentials
4. Visit any page → click the floating "+" button to save it

The extension talks to `http://localhost:8091` (see `extension/manifest.json` `host_permissions` — update for a deployed backend).

## API reference

Every response uses this envelope:
```json
{ "status": "SUCCESS", "data": { ... }, "message": "...", "timestamp": "...", "requestId": "..." }
```

### Auth
| Method | Path | Body |
|---|---|---|
| POST | `/api/auth/register` | `{ email, password, displayName }` |
| POST | `/api/auth/login` | `{ email, password }` |
| POST | `/api/auth/refresh` | `{ refreshToken }` |

### Vault
| Method | Path | Notes |
|---|---|---|
| GET | `/api/vault?page=&size=` | Paginated listing |
| POST | `/api/vault/save` | `{ url, source? }` — returns immediately, enrichment happens async |
| GET | `/api/vault/{id}` | Single item |
| GET | `/api/vault/search?q=` | Context-aware search (not keyword) |
| GET | `/api/vault/resurface?limit=` | Ranked resurfacing feed |
| GET | `/api/vault/forgotten` | 30+ day unviewed high-importance items |
| POST | `/api/vault/{id}/rediscover` | Mark an item as revisited |
| GET | `/api/vault/analytics` | Chart-ready aggregates |
| GET | `/api/vault/digest/today` | Today's 4-item digest |
| POST | `/api/vault/import/json` | `{ urls: [...] }` (max 500) — batched async import |
| GET | `/api/vault/import/status/{jobId}` | Import progress |

### Chrome extension
| Method | Path | Notes |
|---|---|---|
| POST | `/api/chrome/session` | Exchange a JWT for a long-lived `X-Chrome-Token` |
| POST | `/api/chrome/save/quick` | `{ url }`, auth via `X-Chrome-Token` |
| POST | `/api/chrome/save/selection` | `{ url, selectedText }` |

Example:
```bash
curl -X POST http://localhost:8091/api/vault/save \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"url":"https://example.com"}'
```

## Testing

```bash
cd backend && ./mvnw test -Dspring.profiles.active=test   # H2, no Docker needed
```

## Screenshots

_Add screenshots of Dashboard, Vault, Rediscovery, and Analytics here._
