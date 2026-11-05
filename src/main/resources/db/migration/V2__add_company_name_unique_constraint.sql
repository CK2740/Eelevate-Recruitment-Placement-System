ALTER TABLE companies
    ADD CONSTRAINT uq_companies_name UNIQUE (company_name);
