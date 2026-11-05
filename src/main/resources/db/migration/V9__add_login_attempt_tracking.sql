CREATE TABLE login_attempt_tracking (
    identifier_type VARCHAR(8) NOT NULL,
    identifier_hash BINARY(32) NOT NULL,
    failed_attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
    window_started_at DATETIME(6) NULL,
    blocked_until DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (identifier_type, identifier_hash),
    KEY idx_login_attempt_tracking_updated_at (updated_at),
    CONSTRAINT chk_login_attempt_tracking_type CHECK (identifier_type IN ('IP', 'EMAIL')),
    CONSTRAINT chk_login_attempt_tracking_failures CHECK (failed_attempts <= 5)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
