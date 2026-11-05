ALTER TABLE jobs
    ADD COLUMN job_description TEXT NULL AFTER package_amount,
    ADD COLUMN skills TEXT NULL AFTER job_description;
