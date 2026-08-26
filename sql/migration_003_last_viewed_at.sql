ALTER TABLE vault_items
    ADD COLUMN last_viewed_at DATETIME(3) NULL AFTER last_surfaced_at;
