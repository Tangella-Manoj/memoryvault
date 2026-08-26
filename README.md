# MemoryVault

![CI](https://github.com/OWNER/memoryvault/actions/workflows/ci.yml/badge.svg)

People save hundreds of articles, videos, and tweets they mean to revisit — and almost never do. MemoryVault is an intelligence layer over your saved links: it detects what each item is about, understands the emotional and life context you saved it in, and proactively resurfaces forgotten items when they're actually relevant again, instead of leaving them to rot in a bookmarks folder.

**Deployment status:** production infrastructure (`render.yaml`, a Postgres-compatible schema, Vercel config, and the `memoryvault.stacknode.dev` / `api.stacknode.dev` domain wiring) is committed and ready — see [Deploying to production](#deploying-to-production). Nothing has actually been deployed from this environment: that step needs a real Render account, Vercel account, and Name.com DNS access, none of which exist in this sandbox. Until someone runs that step, the app runs locally only (Quick start below).

### The search overlay, live

The Chrome extension's contextual search overlay, actually running against DuckDuckGo — this is a real recording of the real extension, not a mockup:

![MemoryVault's search overlay appearing above DuckDuckGo results while typing "career growth tips"](docs/search-overlay-demo.gif)

## Architecture

All 6 ingestion channels feed the same enrichment pipeline and land in the same vault:

```
                    ┌─ Manual save (web app "+" button) ─────────┐
                    ├─ Chrome auto-capture (Instagram/Twitter) ──┤
                    ├─ Contextual search overlay (read-only) ····┤   (surfaces existing items,
                    ├─ PWA share target (Android) ───────────────┤    doesn't create new ones)
                    ├─ Personal email inbox (SMTP / Mailgun) ────┤
                    └─ YouTube sync (OAuth, 6-hourly) ───────────┘
                                        │
                                        ▼
                    ┌──────────────────────────────────────┐
                    │           Spring Boot REST API         │
                    │  ┌────────────────────────────────┐  │      ┌─────────────┐
                    │  │ @Async enrichment pipeline       │──┼────▶│ Claude API   │
                    │  │ (summarize, tag, context-classify)│  │      │ (optional)   │
                    │  └────────────────────────────────┘  │      └─────────────┘
                    │  ┌────────────────────────────────┐  │
                    │  │ ResurfaceEngine + EngagementPattern│  │      ┌─────────────┐
                    │  │ Service → NotificationScheduler   │──┼────▶│ Web Push /   │
                    │  └────────────────────────────────┘  │      │ FCM (VAPID)  │
                    └──────────────┬─────────────────────────┘      └─────────────┘
                                   │ JDBC
                                   ▼
                    ┌──────────────────────────┐
                    │ MySQL 8 (Docker, local)   │
                    │  — or —                    │
                    │ PostgreSQL (Render, prod)  │
                    └──────────────────────────┘
                                   ▲
                                   │ HTTPS/JSON
                    ┌──────────────────────────┐
                    │ React (Vite) — served      │
                    │ locally or from Vercel     │
                    └──────────────────────────┘
```

**Environment isolation**: Java 17 + Maven via SDKMAN (`backend/.sdkmanrc`) with Maven Wrapper as the real guarantee (`./mvnw` works with no SDKMAN installed). Node 20 via nvm (`frontend/.nvmrc`). MySQL runs only in Docker locally — no system MySQL is ever touched. Production swaps MySQL for a Render-managed PostgreSQL instance via the `prod` Spring profile; nothing else in the app changes (see [Deploying to production](#deploying-to-production)).

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
for f in sql/schema.sql sql/migration_*.sql; do docker exec -i memoryvault_mysql mysql -u"$(grep MYSQL_USER .env|cut -d= -f2)" -p"$(grep MYSQL_PASSWORD .env|cut -d= -f2)" "$(grep MYSQL_DATABASE .env|cut -d= -f2)" < "$f"; done  # 4
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

## Ingestion channels

Beyond manual save and the floating "+" button, items reach a user's vault through five additional channels:

| Channel | How it works |
|---|---|
| **Chrome auto-capture** | `extension/instagram.js` and `extension/twitter.js` watch Instagram's saved-posts grid and Twitter/X bookmarks (MutationObserver, since both are infinite-scroll/virtualized) and POST new items to `/api/chrome/save/quick` automatically — no manual click needed |
| **Contextual search overlay** | `extension/search-overlay.js` runs on google.com/bing.com/duckduckgo.com, debounces the search box (600ms), and calls `GET /api/chrome/search` to surface the caller's own top-3 relevant saved items (context score > 0.4) above the search results |
| **PWA share target** | Installed as a PWA on Android, MemoryVault registers as a native share target — sharing a link from any app (YouTube, a browser, WhatsApp) opens `/share-target`, which POSTs to `/api/vault/share-target` |
| **Personal email inbox** | Every user gets a generated `{8-hex-chars}@vault.stacknode.dev` address (see `GET /api/users/me`) — forwarding any email to it extracts and saves every real URL in the body (tracking pixels, unsubscribe links, and short/redirect URLs are filtered out) |
| **YouTube sync** | Connect a Google account (`GET /api/integrations/youtube/connect`) to auto-import liked videos and Watch Later every 6 hours, deduplicated by video id |

### Setting up YouTube OAuth

1. In [Google Cloud Console](https://console.cloud.google.com/apis/credentials), create an **OAuth 2.0 Client ID** of type "Web application"
2. Add `http://localhost:8091/api/integrations/youtube/callback` as an authorized redirect URI (or your deployed backend's equivalent)
3. Enable the **YouTube Data API v3** for the project
4. Put the client id/secret in `.env`: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
5. Generate `TOKEN_ENCRYPTION_KEY` (AES-256, used to encrypt stored OAuth tokens at rest): `openssl rand -hex 32`

Without these set, `GET /api/integrations/youtube/connect` returns `503` rather than crashing — the rest of the app works fine unconfigured.

### Configuring the email inbox

Two options, either works:

- **Self-hosted (default, zero setup)** — `SMTP_RECEIVER_ENABLED=true` starts an embedded SMTP server (`SmtpReceiverConfig`, port `SMTP_RECEIVER_PORT`, default `2525`) that accepts mail directly. Point real MX records at this host to receive real internet email, or just `swaks`/`smtplib` to `localhost:2525` for local testing.
- **Mailgun** — set `MAILGUN_WEBHOOK_SIGNING_KEY` and route Mailgun's inbound webhook to `POST /api/email/inbound`. Every request's HMAC-SHA256 signature is verified against this key before any email is processed.

`VAULT_EMAIL_DOMAIN` controls the domain half of each generated `{uuid}@domain` address.

### Enabling push notifications

1. Generate a VAPID keypair once: `npx web-push generate-vapid-keys`
2. Put the values in `.env`: `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT` (a `mailto:` contact), and `VITE_VAPID_PUBLIC_KEY` in `frontend/.env` (same public key — safe to expose client-side)
3. Without these set, `PushNotificationSender` silently no-ops (`configured=false`) rather than failing startup

`EngagementPatternService` learns each user's optimal notification hour from their real resurfacing engagement (`resurface_events` where the action is `VIEWED`/`SAVED_AGAIN`), recency-weighted, recalculated every 48 hours — defaulting to 8am UTC until a user has 10+ qualifying events. `NotificationSchedulerService` runs hourly and sends one push ("3 things you saved and forgot") to every user whose optimal hour matches the current UTC hour. The frontend shows a custom in-app modal explaining this before ever requesting the browser's native permission prompt — nothing is requested silently.

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

### Upgrades (Instagram/Twitter capture, PWA share, email, YouTube, notifications)
| Method | Path | Notes |
|---|---|---|
| GET | `/api/chrome/search?q=` | Contextual search overlay results (extension) |
| POST | `/api/vault/share-target` | Form-encoded `{ url?, text?, title? }` — PWA Web Share Target |
| GET | `/api/users/me` | Caller's profile, including generated `vaultEmail` |
| POST | `/api/email/inbound` | Mailgun inbound webhook (HMAC-signed) |
| GET | `/api/integrations/youtube/connect` | Returns a Google OAuth authorization URL |
| GET | `/api/integrations/youtube/callback` | OAuth redirect target — not called directly |
| POST | `/api/integrations/youtube/sync` | Manual sync trigger |
| GET | `/api/integrations/youtube/status` | Connection + last-sync status |
| POST | `/api/integrations/youtube/toggle?enabled=` | Enable/disable the 6-hourly auto-sync |
| GET | `/api/notifications/preferences` | Caller's notification settings |
| POST | `/api/notifications/subscribe` | `{ subscription }` — registers a browser push subscription |
| POST | `/api/notifications/unsubscribe` | Clears the caller's push subscription |
| POST | `/api/notifications/recalculate` | Forces optimal-hour recalculation now |
| POST | `/api/notifications/test-trigger?hour=` | Admin-only — manually fires the hourly scheduler |

Example:
```bash
curl -X POST http://localhost:8091/api/vault/save \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"url":"https://example.com"}'
```

## Deploying to production

Everything needed to deploy is committed; actually running it requires accounts this environment doesn't have credentials for. These are the exact steps.

### 1. Backend on Render

`render.yaml` in the project root defines a `memoryvault-backend` web service (built from `backend/Dockerfile`) and a managed `memoryvault-db` PostgreSQL database, with `healthCheckPath: /actuator/health` and `autoDeploy: true` on push to `main`.

1. Push this repo to GitHub, then in the Render dashboard: **New → Blueprint**, point it at the repo. Render reads `render.yaml` and provisions both resources.
2. Once `memoryvault-db` exists, run the schema against it once: `psql "$DATABASE_URL" -f sql/postgres/schema.sql` (verified locally against a real Postgres 16 container — see `sql/postgres/schema.sql`'s header comment for exactly what's different from the MySQL version and why).
3. Fill in every `sync: false` variable below in the Render dashboard (Environment tab) — these are deliberately not in `render.yaml`, since a secret committed to a blueprint is a secret committed to git history forever.

| Variable | Description | Where to get it |
|---|---|---|
| `JWT_SECRET` | Signs access/refresh tokens — must be ≥256 bits | `openssl rand -hex 32` |
| `ANTHROPIC_API_KEY` | Enables Claude-generated summaries/tags (optional — enrichment falls back to the page's own meta description without it) | [console.anthropic.com](https://console.anthropic.com) |
| `MAILGUN_WEBHOOK_SIGNING_KEY` | Verifies the HMAC signature on inbound email webhooks | Mailgun dashboard → Webhooks (only needed if using Mailgun instead of leaving the self-hosted SMTP receiver, which is disabled in prod — see the note in `render.yaml`) |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | YouTube OAuth sync | Google Cloud Console (see "Setting up YouTube OAuth" above) |
| `TOKEN_ENCRYPTION_KEY` | AES-256 key encrypting stored OAuth tokens at rest | `openssl rand -hex 32` |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | Push notification signing keypair | `npx web-push generate-vapid-keys` |

`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` are wired automatically from the `memoryvault-db` database via `fromDatabase` in `render.yaml` — nothing to fill in for those.

### 2. Frontend on Vercel

1. In the Vercel dashboard: **New Project**, import the same repo, set the root directory to `frontend/`.
2. Vercel auto-detects Vite. `frontend/vercel.json` (committed) handles SPA routing and sets `Service-Worker-Allowed` / manifest headers so the PWA — service worker, share target, offline shell — works correctly once served from a real HTTPS origin (service workers require HTTPS or `localhost`; they silently fail to register anywhere else, which is also why this couldn't be verified on a plain `http://` deployment).
3. `frontend/.env.production` (committed) already points the build at `https://api.stacknode.dev/api`. Override `VITE_VAPID_PUBLIC_KEY` in the Vercel dashboard with the real production VAPID public key generated above — the committed value is a placeholder.

### 3. Custom domains

Both are CNAME records added in Name.com's DNS panel for `stacknode.dev`, not something a Render/Vercel blueprint can do on its own:

| Record | Points to | Where |
|---|---|---|
| `memoryvault` (→ `memoryvault.stacknode.dev`) | Vercel's provided CNAME target (shown in Vercel → Project → Settings → Domains after adding the domain) | Name.com → DNS records for `stacknode.dev` |
| `api` (→ `api.stacknode.dev`) | The Render service's `onrender.com` hostname (shown in Render → Service → Settings → Custom Domains) | Name.com → DNS records for `stacknode.dev` |

After both resolve, set `CORS_ALLOWED_ORIGINS=https://memoryvault.stacknode.dev` on the Render service (already the `render.yaml` default) and add the domain in both dashboards' custom-domain settings so they issue TLS certificates for it.

### 4. Production smoke test

Once both domains resolve, the same walkthrough this project runs locally applies: register at `https://memoryvault.stacknode.dev`, save 3 URLs, confirm enrichment completes within 60s, confirm search returns results, load the extension pointed at `https://api.stacknode.dev/api` (see the comment in `extension/background.js`) and confirm it can save, and open the share-target URL from a mobile browser. None of this has been run against a live deployment from this environment — there is no live deployment yet to run it against.

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

Exercises all 27 endpoints (19 original + 8 covering the 5 upgrades) against the live server and asserts both status codes and the `ApiResponse` envelope shape. Auth requests populate `accessToken`/`refreshToken`/`userId` as collection variables that later requests depend on — run the whole collection, not individual folders out of order.

## How the intelligence score works

Each vault item gets an **importance score** (0–1) at save time: it starts at 0.5 and gains +0.1 each for having an OG image, a summary, and 3+ tags — capped at 1.0. The nightly `BehaviorLearnerService` pass then blends that base score with real engagement: `importance = base × 0.5 + engagementRatio × 0.3 + viewSignal × 0.2`, where `engagementRatio` is the fraction of past resurface events a user actually acted on (viewed/saved-again vs. dismissed), and `viewSignal` is `min(viewCount / 5, 1.0)`.

The **intelligence score** shown on Dashboard/Analytics is a per-user rollup, not a per-item one: `average(importanceScore across all items) × 100`. It's a proxy for "how well-curated and engaged-with is this person's vault" — it rises as items accumulate real engagement signal, not just as more items get saved.

## How the resurfacing algorithm works

Resurfacing — deciding which saved items to show back to a user, and in what order — uses its own formula, separate from the importance score above (`ResurfaceEngine.java`):

```
score = (contextMatch × 0.40 + recencyDecay × 0.30 + engagementHistory × 0.30) × forgottenBonus

recencyDecay   = e^(-0.015 × daysSinceSaved)
forgottenBonus = 1.4  if unseen for 30+ days AND never viewed
                 1.0  otherwise
```

- **contextMatch** (40% weight) comes from `ContextDetector` comparing the item's stored `lifeContext`/`emotionalContext`/tags against the user's own accumulated context weights (`user_contexts` table) — below 20 saved items, this is deliberately skipped in favor of recency + importance, since a from-scratch similarity signal is noisier than no signal at 20 data points.
- **recencyDecay** (30% weight) is exponential, not linear — an item saved yesterday and one saved 3 days ago should barely differ; one saved 90 days ago should differ a lot.
- **engagementHistory** (30% weight) is the fraction of the item's own past `resurface_events` the user actually acted on (`VIEWED`/`SAVED_AGAIN`) versus dismissed — an item that's been shown before and ignored should rank lower next time, not identically.
- **forgottenBonus** exists because the whole point of the product is surfacing things a user would otherwise never see again — an item that's genuinely old and never revisited gets a real, deliberate boost over one that's merely old.

This is also the formula `NotificationSchedulerService` (Upgrade 5) uses to pick which 3 items go into a push notification — the same ranking, just triggered by the clock (a user's learned optimal hour) instead of by a page load.

Separately, `EngagementPatternService` learns *when* to resurface: it buckets each user's `VIEWED`/`SAVED_AGAIN` events by hour-of-day, weights recent events more than old ones (`e^(-0.03 × daysAgo)`), and picks the hour with the highest weighted total as that user's "optimal hour" — recalculated every 48 hours, defaulting to 8am UTC until a user has 10+ qualifying events.

## Architecture decision records

**1. MySQL only in Docker, JWT secret required with no fallback, extension CORS pinned to one id.** Every one of these closes off an entire class of "works differently outside dev" or "silently insecure" bug — a system MySQL fallback, a demo-mode JWT secret, or a wildcard extension origin are exactly the things that quietly ship to production. Fail fast and explicit instead.

**2. Enrichment computed outside any transaction, applied via a short reload+save.** Originally the enrichment service held a JPA entity open across the whole multi-second network round trip (page fetch + Claude call), then blind-saved it at the end — silently clobbering any concurrent write (a view-count increment, a rediscover action) that landed on the row in between. Splitting "slow computation" from "fast persistence" closes that window to milliseconds.

**3. Cross-bean calls for every `@Async`/`@Transactional` boundary, never same-class.** Spring's proxy-based AOP only intercepts calls that go through the bean's proxy — a method calling another method on `this` bypasses it silently, with no error. This project hit that exact bug twice (bulk import ran synchronously instead of async; then its per-item event never fired because there was no real transaction). The fix in both cases was the same: extract the annotated method into its own bean.

**4. A bounded, generously-sized thread pool with `CallerRunsPolicy`, never an unbounded pool or the default `AbortPolicy`.** An unbounded `Executors.newCachedThreadPool()` can exhaust OS threads under burst load; a bounded pool with a too-small queue and the default `AbortPolicy` silently drops work when full (and worse, when that drop happens inside a Spring transaction-synchronization callback, the exception is swallowed entirely — see the bulk-import bug in git history). `CallerRunsPolicy` guarantees backpressure instead of data loss.

**5. Context-aware search/resurfacing over keyword search, with an explicit cold-start fallback.** A from-scratch similarity engine is worth less than users' trust in early results — below 20 items, `ContextDetector` deliberately skips text/context similarity (unreliable signal at that volume) and falls back to recency + importance, rather than returning noisy "smart" results that erode trust in the feature.

## Known limitations

- **No live deployment exists yet.** `render.yaml`, `sql/postgres/schema.sql`, `frontend/vercel.json`, and the `stacknode.dev` domain wiring are all committed and were verified locally (a real Postgres 16 container validated the schema against every JPA entity with `ddl-auto: validate`), but nothing has actually been deployed to Render/Vercel or pointed at real DNS — see [Deploying to production](#deploying-to-production) for the exact remaining steps and why they need real account access this environment doesn't have.
- **Claude API calls have no per-user rate limiting or cost controls** beyond the request-level retry/backoff — a user hitting `/vault/save` at the 30/minute cap repeatedly could still generate meaningful Claude spend.
- **The 401 authentication-entry-point returns the correct status code but Tomcat's default HTML error page, not the app's JSON envelope** — a known, flagged gap, not yet root-caused.
- **No database connection pool tuning for scale** — HikariCP defaults are unchanged; under sustained high concurrency (see the bulk-import bugs found and fixed in git history) this is the next place contention would surface.
- **No per-user Claude API key support** — one shared key/model config for the whole deployment.
- **Digest generation and BehaviorLearner are single-node cron (`@Scheduled`)** — running more than one backend instance would double-run both jobs; there's no distributed lock.
- **The Chrome extension's `host_permissions` and CORS origin are hardcoded to `localhost:8091`** — deploying the backend elsewhere requires updating both (documented above) and repacking the extension.
- **No image optimization or CDN** for saved item thumbnails — `ogImageUrl` is stored and served as-is from the source site.
- **Instagram/Twitter content scripts are untested against real logged-in accounts** — this sandbox has no real Instagram/Twitter credentials, so they were verified via request interception against the real origins with synthetic DOM content instead.
- **PWA share target is unverified on a physical Android device** — no phone or HTTPS tunnel available here; the full flow (SW interception → `/share-target` → save) was verified in a real desktop browser instead.
- **The self-hosted SMTP receiver has no TLS/STARTTLS and no SPF/DKIM verification** — fine for local receipt-and-parse testing, not production-ready for accepting real internet mail without a proper MTA in front of it.
- **YouTube sync has never run against a real Google account** — no OAuth credentials exist in this environment; authorization-URL construction, state validation, and the dedup logic are verified, but the actual token exchange and Data API calls are not.
- **Push notification delivery timing depends on FCM**, not this app — a user's optimal-hour push can arrive slightly after the top of the hour if FCM is slow to deliver to an offline/backgrounded browser.
