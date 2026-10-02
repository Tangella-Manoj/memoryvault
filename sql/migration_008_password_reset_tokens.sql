-- migration_008: password reset tokens table
--
-- Replaces the in-memory ConcurrentHashMap used by AuthService.forgotPassword().
-- Codes are stored hashed (SHA-256 hex) so even DB-level read access cannot be
-- used to reset an account. The `used` flag and expiry column allow the cleanup
-- job (TokenCleanupService) to periodically delete stale rows.
--
-- Run this script against your MySQL dev database:
--   mysql -u root -p memoryvault < sql/migration_008_password_reset_tokens.sql

ALTER TABLE user_integrations
    MODIFY COLUMN access_token TEXT NULL,
    MODIFY COLUMN refresh_token TEXT NULL,
    MODIFY COLUMN token_expires_at DATETIME(3) NULL;

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(64) NOT NULL COMMENT 'SHA-256 hex of the 6-digit OTP — never plain text',
    expires_at DATETIME(3) NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    KEY idx_prt_user_used (user_id, used)
) ENGINE=InnoDB;
