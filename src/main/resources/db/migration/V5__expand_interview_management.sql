ALTER TABLE interviews
    ADD COLUMN interview_type VARCHAR(40) NOT NULL DEFAULT 'TECHNICAL' AFTER interview_round,
    ADD COLUMN round_number SMALLINT UNSIGNED NOT NULL DEFAULT 1 AFTER interview_type,
    ADD COLUMN duration_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 60 AFTER scheduled_at,
    ADD COLUMN location VARCHAR(255) NULL AFTER interview_mode,
    ADD COLUMN meeting_link VARCHAR(1000) NULL AFTER location,
    MODIFY COLUMN status ENUM(
        'SCHEDULED', 'COMPLETED', 'CANCELLED', 'RESCHEDULED', 'NO_SHOW'
    ) NOT NULL DEFAULT 'SCHEDULED';

UPDATE interviews SET status = 'SCHEDULED' WHERE status = 'RESCHEDULED';

CREATE TABLE interview_interviewers (
    interviewer_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    interview_id BIGINT UNSIGNED NOT NULL,
    interviewer_name VARCHAR(160) NOT NULL,
    interviewer_email VARCHAR(254) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (interviewer_id),
    CONSTRAINT fk_interview_interviewers_interview
        FOREIGN KEY (interview_id) REFERENCES interviews (interview_id) ON DELETE CASCADE,
    CONSTRAINT uq_interview_interviewer UNIQUE (interview_id, interviewer_name, interviewer_email),
    KEY ix_interview_interviewers_email (interviewer_email)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE interview_interviewer_feedback (
    feedback_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    interviewer_id BIGINT UNSIGNED NOT NULL,
    technical_rating DECIMAL(3,1) NULL,
    communication_rating DECIMAL(3,1) NULL,
    recommendation ENUM('PASS', 'FAIL', 'ON_HOLD') NOT NULL,
    comments TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (feedback_id),
    CONSTRAINT uq_interviewer_feedback_interviewer UNIQUE (interviewer_id),
    CONSTRAINT chk_interviewer_feedback_technical_rating CHECK (
        technical_rating IS NULL OR technical_rating BETWEEN 0 AND 10
    ),
    CONSTRAINT chk_interviewer_feedback_communication_rating CHECK (
        communication_rating IS NULL OR communication_rating BETWEEN 0 AND 10
    ),
    CONSTRAINT fk_interviewer_feedback_interviewer
        FOREIGN KEY (interviewer_id) REFERENCES interview_interviewers (interviewer_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE interview_status_history (
    history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    interview_id BIGINT UNSIGNED NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    changed_by_user_id BIGINT UNSIGNED NULL,
    change_reason VARCHAR(500) NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (history_id),
    CONSTRAINT fk_interview_status_history_interview
        FOREIGN KEY (interview_id) REFERENCES interviews (interview_id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_status_history_user
        FOREIGN KEY (changed_by_user_id) REFERENCES users (user_id) ON DELETE SET NULL,
    KEY ix_interview_status_history_time (interview_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
