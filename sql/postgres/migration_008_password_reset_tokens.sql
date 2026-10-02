-- migration_008: password reset tokens table (PostgreSQL / Render)
--
-- Replaces the in-memory ConcurrentHashMap used by AuthService.forgotPassword().
-- Also relaxes the NOT NULL constraint on user_integrations token columns so that
-- plugin toggle (a UI-only feature) can create stub records without fake tokens.

ALTER TABLE user_integrations
    ALTER COLUMN access_token DROP NOT NULL,
    ALTER COLUMN refresh_token DROP NOT NULL,
    ALTER COLUMN token_expires_at DROP NOT NULL;

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,  -- SHA-256 hex of the 6-digit OTP
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prt_user_used ON password_reset_tokens(user_id, used);
