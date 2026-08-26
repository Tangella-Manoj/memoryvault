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

## Prerequisites

| Tool | Exact version used | Why exact |
|---|---|---|
| Java | 17.0.13-tem (Temurin) | Pinned in `backend/.sdkmanrc`; `./mvnw` works even without this installed |
| Maven | 3.9.9 | Pinned in `backend/.sdkmanrc`; `./mvnw` carries its own 3.9.16 regardless |
| Node | 20.20.2 | Pinned in `frontend/.nvmrc` — 20.19.0+ required by the frontend build tooling |
| Docker | any recent version with Compose v2 | Runs MySQL 8.0 and phpMyAdmin; nothing MySQL-related touches the host |
| Chrome | any recent version | Only needed to load the extension from `extension/` |

Nothing else needs to be installed globally — SDKMAN and nvm manage Java/Node inside the project, and MySQL never runs outside Docker.

## Quick start (fresh machine, under 10 commands)

```bash
git clone <repo-url> memoryvault && cd memoryvault                      # 1
cp .env.example .env && sed -i '' "s/JWT_SECRET=.*/JWT_SECRET=$(openssl rand -hex 32)/" .env  # 2
docker compose up -d                                                     # 3
for f in sql/schema.sql sql/migration_00{1,2,3}_*.sql; do docker exec -i memoryvault_mysql mysql -u"$(grep MYSQL_USER .env|cut -d= -f2)" -p"$(grep MYSQL_PASSWORD .env|cut -d= -f2)" "$(grep MYSQL_DATABASE .env|cut -d= -f2)" < "$f"; done  # 4
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev &    # 5
cd ../frontend && nvm use && npm install && npm run dev &                # 6
```

Frontend at `http://localhost:5173`, backend at `http://localhost:8091`. `ANTHROPIC_API_KEY` is left as a placeholder — the app works without it (enrichment falls back to the page's own meta description instead of a Claude summary).

## Load the Chrome extension

1. `chrome://extensions` → enable Developer mode
2. "Load unpacked" → select the `extension/` folder
3. Click the MemoryVault icon → log in with your app credentials
4. Visit any page → click the floating "+" button to save it

The extension talks to `http://localhost:8091` (see `extension/manifest.json` `host_permissions` — update for a deployed backend).

### Chrome extension CORS

The backend's CORS config (`SecurityConfig.corsConfigurationSource`) pins the allowed
extension origin to one exact id — `chrome-extension://akcoccffjeibkkebonenilckdihakfjh`
— not a `chrome-extension://*` wildcard, so no other installed extension can call the API.

`extension/manifest.json` carries a `"key"` field (the extension's public key, base64
DER) that makes this id **deterministic**: loading the extension unpacked or packing it
into a `.crx` both produce the same id, as long as they're signed with the matching
private key at `extension-keys/key.pem` (gitignored — generated locally, never committed).

If you regenerate the keypair (or fork this project and want your own identity):

```bash
cd extension-keys
openssl genrsa -out key.pem 2048
openssl rsa -in key.pem -pubout -outform DER -out key.pub.der
python3 -c "
import base64, hashlib
der = open('key.pub.der','rb').read()
print('key field (put in manifest.json):', base64.b64encode(der).decode())
digest = hashlib.sha256(der).digest()
print('extension id (put in CorsConfigurationSource):',
      ''.join(chr(97 + (b >> 4)) + chr(97 + (b & 0xf)) for b in digest[:16]))
"
```

Then update both `extension/manifest.json`'s `"key"` field and the id in
`SecurityConfig.corsConfigurationSource` with the printed values, and reload the
unpacked extension.

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
| POST | `/api/auth/logout` | `{ refreshToken }` — revokes it |

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

### Unit / integration tests (H2, no Docker needed)

```bash
cd backend && ./mvnw test
```

Runs against an in-memory H2 database with a fixed test-only JWT secret (`application-test.yml`) — no environment setup required. Covers auth authorization rules (`SecurityUtilTest`), JWT secret validation (`JwtServiceTest`), and the intelligence engine against 20 seeded items (`IntelligenceEngineTest`: ranking correctness, the forgotten-item bonus, the 30-day threshold).

### Postman / Newman (against a running server)

```bash
docker compose up -d && cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev &
npx newman run memoryvault.postman_collection.json
```

Exercises all 19 endpoints against the live server and asserts both status codes and the `ApiResponse` envelope shape. Auth requests populate `accessToken`/`refreshToken`/`userId` as collection variables that later requests depend on — run the whole collection, not individual folders out of order.

## How the intelligence score works

Each vault item gets an **importance score** (0–1) at save time: it starts at 0.5 and gains +0.1 each for having an OG image, a summary, and 3+ tags — capped at 1.0. The nightly `BehaviorLearnerService` pass then blends that base score with real engagement: `importance = base × 0.5 + engagementRatio × 0.3 + viewSignal × 0.2`, where `engagementRatio` is the fraction of past resurface events a user actually acted on (viewed/saved-again vs. dismissed), and `viewSignal` is `min(viewCount / 5, 1.0)`.

The **intelligence score** shown on Dashboard/Analytics is a per-user rollup, not a per-item one: `average(importanceScore across all items) × 100`. It's a proxy for "how well-curated and engaged-with is this person's vault" — it rises as items accumulate real engagement signal, not just as more items get saved.

Separately, **resurfacing** uses its own formula (not the importance score directly) — see `ResurfaceEngine`'s Javadoc: context match, recency decay, engagement history, and a 1.4x bonus for items unseen 30+ days.

## Architecture decision records

**1. MySQL only in Docker, JWT secret required with no fallback, extension CORS pinned to one id.** Every one of these closes off an entire class of "works differently outside dev" or "silently insecure" bug — a system MySQL fallback, a demo-mode JWT secret, or a wildcard extension origin are exactly the things that quietly ship to production. Fail fast and explicit instead.

**2. Enrichment computed outside any transaction, applied via a short reload+save.** Originally the enrichment service held a JPA entity open across the whole multi-second network round trip (page fetch + Claude call), then blind-saved it at the end — silently clobbering any concurrent write (a view-count increment, a rediscover action) that landed on the row in between. Splitting "slow computation" from "fast persistence" closes that window to milliseconds.

**3. Cross-bean calls for every `@Async`/`@Transactional` boundary, never same-class.** Spring's proxy-based AOP only intercepts calls that go through the bean's proxy — a method calling another method on `this` bypasses it silently, with no error. This project hit that exact bug twice (bulk import ran synchronously instead of async; then its per-item event never fired because there was no real transaction). The fix in both cases was the same: extract the annotated method into its own bean.

**4. A bounded, generously-sized thread pool with `CallerRunsPolicy`, never an unbounded pool or the default `AbortPolicy`.** An unbounded `Executors.newCachedThreadPool()` can exhaust OS threads under burst load; a bounded pool with a too-small queue and the default `AbortPolicy` silently drops work when full (and worse, when that drop happens inside a Spring transaction-synchronization callback, the exception is swallowed entirely — see the bulk-import bug in git history). `CallerRunsPolicy` guarantees backpressure instead of data loss.

**5. Context-aware search/resurfacing over keyword search, with an explicit cold-start fallback.** A from-scratch similarity engine is worth less than users' trust in early results — below 20 items, `ContextDetector` deliberately skips text/context similarity (unreliable signal at that volume) and falls back to recency + importance, rather than returning noisy "smart" results that erode trust in the feature.

## Known limitations

- **No production deployment exists.** Everything here runs locally against Docker MySQL; there is no live/hosted URL for this project.
- **Claude API calls have no per-user rate limiting or cost controls** beyond the request-level retry/backoff — a user hitting `/vault/save` at the 30/minute cap repeatedly could still generate meaningful Claude spend.
- **The 401 authentication-entry-point returns the correct status code but Tomcat's default HTML error page, not the app's JSON envelope** — a known, flagged gap, not yet root-caused.
- **No database connection pool tuning for scale** — HikariCP defaults are unchanged; under sustained high concurrency (see the bulk-import bugs found and fixed in git history) this is the next place contention would surface.
- **No per-user Claude API key support** — one shared key/model config for the whole deployment.
- **Digest generation and BehaviorLearner are single-node cron (`@Scheduled`)** — running more than one backend instance would double-run both jobs; there's no distributed lock.
- **The Chrome extension's `host_permissions` and CORS origin are hardcoded to `localhost:8091`** — deploying the backend elsewhere requires updating both (documented above) and repacking the extension.
- **No image optimization or CDN** for saved item thumbnails — `ogImageUrl` is stored and served as-is from the source site.
