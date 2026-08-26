-- MemoryVault schema — MySQL 8.0

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    timezone VARCHAR(60) NOT NULL DEFAULT 'UTC',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB;

CREATE TABLE vault_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    url VARCHAR(2048) NOT NULL,
    title VARCHAR(500),
    summary TEXT,
    og_image_url VARCHAR(2048),
    content_type ENUM('ARTICLE','VIDEO','TWEET','THREAD','PRODUCT','REPO','DOCUMENT','IMAGE','OTHER')
        NOT NULL DEFAULT 'OTHER',
    status ENUM('PROCESSING','PROCESSED','FAILED') NOT NULL DEFAULT 'PROCESSING',
    emotional_context ENUM('INSPIRED','CURIOUS','ANXIOUS','NOSTALGIC','EXCITED','CALM','NEUTRAL')
        NOT NULL DEFAULT 'NEUTRAL',
    life_context ENUM('CAREER','HEALTH','RELATIONSHIPS','FINANCE','LEARNING','CREATIVITY','TRAVEL','HOME','OTHER')
        NOT NULL DEFAULT 'OTHER',
    importance_score DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
    source ENUM('WEB','CHROME_EXTENSION','BULK_IMPORT') NOT NULL DEFAULT 'WEB',
    saved_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_surfaced_at DATETIME(3) NULL,
    view_count INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_vault_items_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    KEY idx_vault_items_user_saved (user_id, saved_at),
    KEY idx_vault_items_user_status (user_id, status),
    KEY idx_vault_items_user_importance (user_id, importance_score)
) ENGINE=InnoDB;

CREATE TABLE tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    UNIQUE KEY uq_tags_name (name)
) ENGINE=InnoDB;

CREATE TABLE vault_item_tags (
    vault_item_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (vault_item_id, tag_id),
    CONSTRAINT fk_vit_item FOREIGN KEY (vault_item_id) REFERENCES vault_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_vit_tag FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE,
    KEY idx_vit_tag (tag_id)
) ENGINE=InnoDB;

CREATE TABLE user_contexts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    context_type VARCHAR(60) NOT NULL,
    weight DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_user_contexts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_user_contexts (user_id, context_type)
) ENGINE=InnoDB;

CREATE TABLE resurface_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vault_item_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    score DECIMAL(6,4) NOT NULL,
    shown_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    action ENUM('SHOWN','VIEWED','DISMISSED','SAVED_AGAIN') NOT NULL DEFAULT 'SHOWN',
    CONSTRAINT fk_resurface_item FOREIGN KEY (vault_item_id) REFERENCES vault_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_resurface_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    KEY idx_resurface_user_shown (user_id, shown_at)
) ENGINE=InnoDB;

CREATE TABLE user_behavior_patterns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    pattern_type VARCHAR(60) NOT NULL,
    pattern_value JSON NOT NULL,
    computed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_behavior_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    KEY idx_behavior_user_type (user_id, pattern_type)
) ENGINE=InnoDB;

CREATE TABLE import_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status ENUM('QUEUED','RUNNING','DONE','FAILED') NOT NULL DEFAULT 'QUEUED',
    total INT NOT NULL DEFAULT 0,
    processed INT NOT NULL DEFAULT 0,
    failed INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    CONSTRAINT fk_import_jobs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    KEY idx_import_jobs_user_status (user_id, status)
) ENGINE=InnoDB;

CREATE TABLE daily_digests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    digest_date DATE NOT NULL,
    item_ids JSON NOT NULL,
    sent_at DATETIME(3) NULL,
    CONSTRAINT fk_digests_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_digests_user_date (user_id, digest_date)
) ENGINE=InnoDB;

CREATE TABLE chrome_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_token VARCHAR(255) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_chrome_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_chrome_sessions_token (session_token)
) ENGINE=InnoDB;
