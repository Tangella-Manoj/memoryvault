-- MemoryVault schema — PostgreSQL (Render managed database)
--
-- This is the consolidated equivalent of sql/schema.sql + all sql/migration_*.sql
-- applied in order, translated for Postgres. It targets a fresh database only —
-- there is no existing Postgres data to migrate incrementally, so one file
-- reflecting the final state is simpler and less error-prone than replaying
-- six MySQL-specific ALTER TABLEs.
--
-- Differences from the MySQL version, and why:
--   * BIGINT ... AUTO_INCREMENT      -> BIGINT GENERATED ALWAYS AS IDENTITY (SQL:2008, Postgres-native)
--   * ENUM('A','B',...)               -> VARCHAR + CHECK constraint (Hibernate maps @Enumerated(STRING)
--                                        to a plain VARCHAR column on every dialect; MySQL's native ENUM
--                                        was already just a MySQL-specific optimization, not something
--                                        the app relies on)
--   * DATETIME(3) / CURRENT_TIMESTAMP(3) -> TIMESTAMP(3) / CURRENT_TIMESTAMP (Postgres has no columnar
--                                        fractional-seconds-in-the-type-name syntax, but TIMESTAMP(3)
--                                        gives the same millisecond precision)
--   * ON UPDATE CURRENT_TIMESTAMP      -> dropped; both entities that use it (User, UserContext) already
--                                        set updated_at themselves via @PreUpdate, so the DB-side
--                                        auto-update was always redundant, not load-bearing
--   * KEY name (...) inline            -> separate CREATE INDEX statements (Postgres has no inline
--                                        non-unique KEY clause in CREATE TABLE)
--   * ENGINE=InnoDB                    -> removed (MySQL storage-engine directive, meaningless here)
--   * json columnDefinition             -> unchanged; Postgres has a native `json` type under the same
--                                        name, so the JPA entities' columnDefinition = "json" needs no
--                                        per-dialect override

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    vault_email VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','ADMIN')),
    timezone VARCHAR(60) NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_vault_email UNIQUE (vault_email)
);

CREATE TABLE vault_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    url VARCHAR(2048) NOT NULL,
    title VARCHAR(500),
    summary TEXT,
    embedding TEXT,
    og_image_url VARCHAR(2048),
    content_type VARCHAR(20) NOT NULL DEFAULT 'OTHER'
        CHECK (content_type IN ('ARTICLE','VIDEO','TWEET','THREAD','PRODUCT','REPO','DOCUMENT','IMAGE','OTHER')),
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING'
        CHECK (status IN ('PROCESSING','PROCESSED','FAILED')),
    emotional_context VARCHAR(20) NOT NULL DEFAULT 'NEUTRAL'
        CHECK (emotional_context IN ('INSPIRED','CURIOUS','ANXIOUS','NOSTALGIC','EXCITED','CALM','NEUTRAL')),
    life_context VARCHAR(20) NOT NULL DEFAULT 'OTHER'
        CHECK (life_context IN ('CAREER','HEALTH','RELATIONSHIPS','FINANCE','LEARNING','CREATIVITY','TRAVEL','HOME','OTHER')),
    importance_score DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
    source VARCHAR(20) NOT NULL DEFAULT 'WEB'
        CHECK (source IN ('WEB','CHROME_EXTENSION','BULK_IMPORT','INSTAGRAM','TWITTER','YOUTUBE','EMAIL')),
    external_id VARCHAR(255),
    saved_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_surfaced_at TIMESTAMP(3),
    last_viewed_at TIMESTAMP(3),
    view_count INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_vault_items_user_external UNIQUE (user_id, external_id)
);
CREATE INDEX idx_vault_items_user_saved ON vault_items (user_id, saved_at);
CREATE INDEX idx_vault_items_user_status ON vault_items (user_id, status);
CREATE INDEX idx_vault_items_user_importance ON vault_items (user_id, importance_score);

CREATE TABLE tags (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    CONSTRAINT uq_tags_name UNIQUE (name)
);

CREATE TABLE vault_item_tags (
    vault_item_id BIGINT NOT NULL REFERENCES vault_items(id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (vault_item_id, tag_id)
);
CREATE INDEX idx_vit_tag ON vault_item_tags (tag_id);

CREATE TABLE user_contexts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    context_type VARCHAR(60) NOT NULL,
    weight DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_contexts UNIQUE (user_id, context_type)
);

CREATE TABLE resurface_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vault_item_id BIGINT NOT NULL REFERENCES vault_items(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(500) NOT NULL,
    score DECIMAL(6,4) NOT NULL,
    shown_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    action VARCHAR(20) NOT NULL DEFAULT 'SHOWN'
        CHECK (action IN ('SHOWN','VIEWED','DISMISSED','SAVED_AGAIN'))
);
CREATE INDEX idx_resurface_user_shown ON resurface_events (user_id, shown_at);

CREATE TABLE user_behavior_patterns (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pattern_type VARCHAR(60) NOT NULL,
    pattern_value JSON NOT NULL,
    computed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_behavior_user_type ON user_behavior_patterns (user_id, pattern_type);

CREATE TABLE import_jobs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED','RUNNING','DONE','FAILED')),
    total INT NOT NULL DEFAULT 0,
    processed INT NOT NULL DEFAULT 0,
    failed INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP(3)
);
CREATE INDEX idx_import_jobs_user_status ON import_jobs (user_id, status);

CREATE TABLE daily_digests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    digest_date DATE NOT NULL,
    item_ids JSON NOT NULL,
    sent_at TIMESTAMP(3),
    CONSTRAINT uq_digests_user_date UNIQUE (user_id, digest_date)
);

CREATE TABLE chrome_sessions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_token VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chrome_sessions_token UNIQUE (session_token)
);

CREATE TABLE refresh_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE user_integrations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('YOUTUBE','GITHUB','OBSIDIAN','NOTION','RAYCAST','SLACK','TELEGRAM','WEBHOOK')),
    -- Nullable: populated only after a real OAuth flow completes. Plugin-toggle stubs leave these NULL.
    access_token TEXT,
    refresh_token TEXT,
    token_expires_at TIMESTAMP(3),
    last_synced_at TIMESTAMP(3),
    sync_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_integrations_user_platform UNIQUE (user_id, platform)
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,  -- SHA-256 hex of the 6-digit OTP — never stored plain
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_prt_user_used ON password_reset_tokens(user_id, used);

CREATE TABLE user_notification_preferences (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    optimal_hour INT NOT NULL DEFAULT 8,
    optimal_day_of_week INT,
    push_subscription_json TEXT,
    notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_calculated_at TIMESTAMP(3),
    CONSTRAINT uq_notification_prefs_user UNIQUE (user_id)
);
