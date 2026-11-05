CREATE TABLE candidate_portal_profiles (
    user_id BIGINT UNSIGNED NOT NULL,
    candidate_id BIGINT UNSIGNED NULL,
    mobile VARCHAR(30) NULL,
    qualification VARCHAR(160) NULL,
    passing_year SMALLINT UNSIGNED NULL,
    percentage DECIMAL(5,2) NULL,
    backlog_count INT UNSIGNED NOT NULL DEFAULT 0,
    skills TEXT NULL,
    experience TEXT NULL,
    resume_original_name VARCHAR(255) NULL,
    resume_storage_key VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT uq_candidate_portal_candidate UNIQUE (candidate_id),
    CONSTRAINT fk_candidate_portal_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_candidate_portal_candidate
        FOREIGN KEY (candidate_id) REFERENCES candidates (candidate_id) ON DELETE SET NULL,
    CONSTRAINT chk_candidate_portal_percentage CHECK (
        percentage IS NULL OR (percentage >= 0 AND percentage <= 100)
    ),
    CONSTRAINT chk_candidate_portal_year CHECK (
        passing_year IS NULL OR passing_year BETWEEN 1901 AND 2155
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO candidate_portal_profiles (user_id, candidate_id, mobile, qualification,
    passing_year, percentage, backlog_count, skills)
SELECT u.user_id, c.candidate_id, c.mobile, c.qualification, c.passing_year,
       c.percentage, c.backlog_count, c.skills
FROM users u
JOIN user_roles ur ON ur.user_id = u.user_id
JOIN roles r ON r.role_id = ur.role_id AND r.role_code = 'CANDIDATE'
LEFT JOIN candidates c ON LOWER(c.email) = LOWER(u.email);
