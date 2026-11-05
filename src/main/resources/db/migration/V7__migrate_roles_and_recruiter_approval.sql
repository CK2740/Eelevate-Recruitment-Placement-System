ALTER TABLE users
    ADD COLUMN account_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED';

INSERT INTO roles (role_code, role_name, description)
VALUES ('CANDIDATE', 'Candidate', 'Browse jobs and manage candidate applications');

INSERT IGNORE INTO user_roles (user_id, role_id, granted_at)
SELECT user_roles.user_id, recruiter.role_id, user_roles.granted_at
FROM user_roles
JOIN roles placement ON placement.role_id = user_roles.role_id
JOIN roles recruiter ON recruiter.role_code = 'RECRUITER'
WHERE placement.role_code = 'PLACEMENT_OFFICER';

DELETE user_roles
FROM user_roles
JOIN roles ON roles.role_id = user_roles.role_id
WHERE roles.role_code = 'PLACEMENT_OFFICER';

DELETE FROM roles WHERE role_code = 'PLACEMENT_OFFICER';
