ALTER TABLE offers
    ADD COLUMN expiry_date DATE NULL AFTER offer_date,
    ADD COLUMN offer_details TEXT NULL AFTER expiry_date,
    MODIFY COLUMN status ENUM(
        'DRAFT', 'ISSUED', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'WITHDRAWN',
        'PENDING', 'REJECTED'
    ) NOT NULL DEFAULT 'DRAFT';

UPDATE offers SET status = 'PENDING' WHERE status IN ('DRAFT', 'ISSUED');
UPDATE offers SET status = 'REJECTED' WHERE status = 'DECLINED';

ALTER TABLE offers
    MODIFY COLUMN status ENUM('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'WITHDRAWN')
        NOT NULL DEFAULT 'PENDING';

CREATE TABLE offer_status_history (
    history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    offer_id BIGINT UNSIGNED NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    changed_by_user_id BIGINT UNSIGNED NULL,
    change_reason VARCHAR(500) NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (history_id),
    CONSTRAINT fk_offer_status_history_offer
        FOREIGN KEY (offer_id) REFERENCES offers (offer_id) ON DELETE CASCADE,
    CONSTRAINT fk_offer_status_history_user
        FOREIGN KEY (changed_by_user_id) REFERENCES users (user_id) ON DELETE SET NULL,
    KEY ix_offer_status_history_time (offer_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE placement_status_history (
    history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    placement_id BIGINT UNSIGNED NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    changed_by_user_id BIGINT UNSIGNED NULL,
    change_reason VARCHAR(500) NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (history_id),
    CONSTRAINT fk_placement_status_history_placement
        FOREIGN KEY (placement_id) REFERENCES placements (placement_id) ON DELETE CASCADE,
    CONSTRAINT fk_placement_status_history_user
        FOREIGN KEY (changed_by_user_id) REFERENCES users (user_id) ON DELETE SET NULL,
    KEY ix_placement_status_history_time (placement_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
