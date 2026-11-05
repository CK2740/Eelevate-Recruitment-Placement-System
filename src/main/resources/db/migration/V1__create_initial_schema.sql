CREATE TABLE roles (
    role_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(40) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id),
    CONSTRAINT uq_roles_role_code UNIQUE (role_code)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE users (
    user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    KEY ix_user_roles_role_id (role_id),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id) REFERENCES roles (role_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE candidates (
    candidate_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL,
    mobile VARCHAR(30) NOT NULL,
    qualification VARCHAR(160) NOT NULL,
    passing_year SMALLINT UNSIGNED NOT NULL,
    percentage DECIMAL(5,2) NOT NULL,
    backlog_count INT UNSIGNED NOT NULL DEFAULT 0,
    skills TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (candidate_id),
    CONSTRAINT uq_candidates_email UNIQUE (email),
    CONSTRAINT uq_candidates_mobile UNIQUE (mobile),
    CONSTRAINT chk_candidates_percentage CHECK (percentage >= 0 AND percentage <= 100),
    CONSTRAINT chk_candidates_passing_year CHECK (passing_year BETWEEN 1901 AND 2155)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE companies (
    company_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_name VARCHAR(200) NOT NULL,
    industry VARCHAR(120) NULL,
    location VARCHAR(200) NULL,
    contact_person VARCHAR(160) NULL,
    email VARCHAR(254) NULL,
    mobile VARCHAR(30) NULL,
    website VARCHAR(255) NULL,
    status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (company_id),
    CONSTRAINT uq_companies_email UNIQUE (email),
    CONSTRAINT uq_companies_mobile UNIQUE (mobile),
    KEY ix_companies_name (company_name),
    KEY ix_companies_status (status)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE jobs (
    job_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id BIGINT UNSIGNED NOT NULL,
    job_title VARCHAR(180) NOT NULL,
    technology VARCHAR(180) NOT NULL,
    openings INT UNSIGNED NOT NULL,
    location VARCHAR(200) NOT NULL,
    package_amount DECIMAL(12,2) NOT NULL,
    application_deadline DATE NOT NULL,
    employment_type ENUM('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP') NOT NULL,
    status ENUM('DRAFT', 'OPEN', 'CLOSED') NOT NULL DEFAULT 'DRAFT',
    created_by_user_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (job_id),
    CONSTRAINT chk_jobs_openings CHECK (openings > 0),
    CONSTRAINT chk_jobs_package_amount CHECK (package_amount >= 0),
    CONSTRAINT fk_jobs_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id) ON DELETE RESTRICT,
    CONSTRAINT fk_jobs_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id) ON DELETE SET NULL,
    KEY ix_jobs_company_status (company_id, status),
    KEY ix_jobs_status_deadline (status, application_deadline),
    KEY ix_jobs_technology (technology)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_eligibility_criteria (
    job_id BIGINT UNSIGNED NOT NULL,
    minimum_percentage DECIMAL(5,2) NOT NULL DEFAULT 0,
    minimum_passing_year SMALLINT UNSIGNED NULL,
    maximum_passing_year SMALLINT UNSIGNED NULL,
    maximum_backlogs INT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (job_id),
    CONSTRAINT chk_eligibility_percentage CHECK (
        minimum_percentage >= 0 AND minimum_percentage <= 100
    ),
    CONSTRAINT chk_eligibility_year_range CHECK (
        minimum_passing_year IS NULL
        OR maximum_passing_year IS NULL
        OR minimum_passing_year <= maximum_passing_year
    ),
    CONSTRAINT chk_eligibility_minimum_year CHECK (
        minimum_passing_year IS NULL OR minimum_passing_year BETWEEN 1901 AND 2155
    ),
    CONSTRAINT chk_eligibility_maximum_year CHECK (
        maximum_passing_year IS NULL OR maximum_passing_year BETWEEN 1901 AND 2155
    ),
    CONSTRAINT fk_eligibility_job
        FOREIGN KEY (job_id) REFERENCES jobs (job_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE applications (
    application_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    candidate_id BIGINT UNSIGNED NOT NULL,
    job_id BIGINT UNSIGNED NOT NULL,
    status ENUM(
        'APPLIED', 'SHORTLISTED', 'INTERVIEW', 'SELECTED', 'OFFERED', 'JOINED', 'REJECTED'
    ) NOT NULL DEFAULT 'APPLIED',
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (application_id),
    CONSTRAINT uq_applications_candidate_job UNIQUE (candidate_id, job_id),
    CONSTRAINT fk_applications_candidate
        FOREIGN KEY (candidate_id) REFERENCES candidates (candidate_id) ON DELETE RESTRICT,
    CONSTRAINT fk_applications_job
        FOREIGN KEY (job_id) REFERENCES jobs (job_id) ON DELETE RESTRICT,
    KEY ix_applications_job_status (job_id, status),
    KEY ix_applications_status_applied_at (status, applied_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE application_status_history (
    history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_id BIGINT UNSIGNED NOT NULL,
    previous_status ENUM(
        'APPLIED', 'SHORTLISTED', 'INTERVIEW', 'SELECTED', 'OFFERED', 'JOINED', 'REJECTED'
    ) NULL,
    new_status ENUM(
        'APPLIED', 'SHORTLISTED', 'INTERVIEW', 'SELECTED', 'OFFERED', 'JOINED', 'REJECTED'
    ) NOT NULL,
    changed_by_user_id BIGINT UNSIGNED NULL,
    change_reason VARCHAR(500) NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (history_id),
    CONSTRAINT fk_application_history_application
        FOREIGN KEY (application_id) REFERENCES applications (application_id) ON DELETE RESTRICT,
    CONSTRAINT fk_application_history_user
        FOREIGN KEY (changed_by_user_id) REFERENCES users (user_id) ON DELETE SET NULL,
    KEY ix_application_history_application_time (application_id, changed_at),
    KEY ix_application_history_user (changed_by_user_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE interviews (
    interview_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_id BIGINT UNSIGNED NOT NULL,
    interview_round VARCHAR(100) NOT NULL,
    scheduled_at DATETIME NOT NULL,
    interview_mode ENUM('IN_PERSON', 'VIDEO', 'PHONE') NOT NULL,
    interviewer VARCHAR(160) NULL,
    status ENUM('SCHEDULED', 'COMPLETED', 'CANCELLED', 'RESCHEDULED') NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (interview_id),
    CONSTRAINT fk_interviews_application
        FOREIGN KEY (application_id) REFERENCES applications (application_id) ON DELETE RESTRICT,
    KEY ix_interviews_application_schedule (application_id, scheduled_at),
    KEY ix_interviews_status_schedule (status, scheduled_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE interview_feedback (
    interview_id BIGINT UNSIGNED NOT NULL,
    technical_rating DECIMAL(3,1) NULL,
    communication_rating DECIMAL(3,1) NULL,
    remarks TEXT NULL,
    result ENUM('PASS', 'FAIL', 'ON_HOLD') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (interview_id),
    CONSTRAINT chk_feedback_technical_rating CHECK (
        technical_rating IS NULL OR technical_rating BETWEEN 0 AND 10
    ),
    CONSTRAINT chk_feedback_communication_rating CHECK (
        communication_rating IS NULL OR communication_rating BETWEEN 0 AND 10
    ),
    CONSTRAINT fk_interview_feedback_interview
        FOREIGN KEY (interview_id) REFERENCES interviews (interview_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE offers (
    offer_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_id BIGINT UNSIGNED NOT NULL,
    designation VARCHAR(180) NOT NULL,
    package_amount DECIMAL(12,2) NOT NULL,
    offer_date DATE NOT NULL,
    joining_date DATE NULL,
    status ENUM('DRAFT', 'ISSUED', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'WITHDRAWN')
        NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (offer_id),
    CONSTRAINT chk_offers_package_amount CHECK (package_amount >= 0),
    CONSTRAINT fk_offers_application
        FOREIGN KEY (application_id) REFERENCES applications (application_id) ON DELETE RESTRICT,
    KEY ix_offers_application_status (application_id, status),
    KEY ix_offers_joining_date (joining_date)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE placements (
    placement_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_id BIGINT UNSIGNED NOT NULL,
    designation VARCHAR(180) NOT NULL,
    final_package DECIMAL(12,2) NOT NULL,
    actual_joining_date DATE NULL,
    joining_status ENUM('PENDING', 'JOINED', 'NOT_JOINED') NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (placement_id),
    CONSTRAINT uq_placements_application UNIQUE (application_id),
    CONSTRAINT chk_placements_final_package CHECK (final_package >= 0),
    CONSTRAINT fk_placements_application
        FOREIGN KEY (application_id) REFERENCES applications (application_id) ON DELETE RESTRICT,
    KEY ix_placements_joining_status (joining_status),
    KEY ix_placements_joining_date (actual_joining_date)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_logs (
    audit_log_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id BIGINT UNSIGNED NULL,
    old_values JSON NULL,
    new_values JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_log_id),
    CONSTRAINT fk_audit_logs_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE SET NULL,
    KEY ix_audit_logs_entity (entity_type, entity_id, created_at),
    KEY ix_audit_logs_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO roles (role_code, role_name, description)
VALUES
    ('ADMIN', 'Administrator', 'Full system administration access'),
    ('RECRUITER', 'Recruiter', 'Manage recruitment activities'),
    ('PLACEMENT_OFFICER', 'Placement Officer', 'Manage placement activities');
