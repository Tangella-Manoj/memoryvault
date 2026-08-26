ALTER TABLE vault_items
    MODIFY COLUMN source ENUM('WEB','CHROME_EXTENSION','BULK_IMPORT','INSTAGRAM','TWITTER','YOUTUBE','EMAIL')
    NOT NULL DEFAULT 'WEB';

ALTER TABLE vault_items
    ADD COLUMN external_id VARCHAR(255) NULL AFTER source,
    ADD UNIQUE KEY uq_vault_items_user_external (user_id, external_id);

ALTER TABLE users
    ADD COLUMN vault_email VARCHAR(255) NULL AFTER email,
    ADD UNIQUE KEY uq_users_vault_email (vault_email);
