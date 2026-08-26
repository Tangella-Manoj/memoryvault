CREATE TABLE user_notification_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    optimal_hour INT NOT NULL DEFAULT 8,
    optimal_day_of_week INT NULL,
    push_subscription_json TEXT NULL,
    notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_calculated_at DATETIME(3) NULL,
    CONSTRAINT fk_notification_prefs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_notification_prefs_user (user_id)
) ENGINE=InnoDB;
