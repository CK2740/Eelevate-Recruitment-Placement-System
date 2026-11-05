package com.recruitment.service;

import com.recruitment.dao.CompanyDao;
import com.recruitment.dto.CompanyPageResponse;
import com.recruitment.dto.CompanyRequest;
import com.recruitment.dto.CompanyResponse;
import com.recruitment.dto.CompanyStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyService {

    private final CompanyDao companyDao;

    public CompanyService(CompanyDao companyDao) {
        this.companyDao = companyDao;
    }

    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        try {
            long companyId = companyDao.insert(request);
            return companyDao.findById(companyId);
        } catch (DuplicateKeyException exception) {
            throw duplicateCompanyException();
        }
    }

    @Transactional(readOnly = true)
    public CompanyResponse getById(long companyId) {
        return findByIdOrNotFound(companyId);
    }

    @Transactional(readOnly = true)
    public CompanyPageResponse getCompanies(
            String search,
            String name,
            String industry,
            String location,
            CompanyStatus status,
            int page,
            int size) {
        return companyDao.findAll(search, name, industry, location, status, page, size);
    }

    @Transactional
    public CompanyResponse update(long companyId, CompanyRequest request) {
        findByIdOrNotFound(companyId);
        try {
            companyDao.update(companyId, request);
            return findByIdOrNotFound(companyId);
        } catch (DuplicateKeyException exception) {
            throw duplicateCompanyException();
        }
    }

    @Transactional
    public void delete(long companyId) {
        findByIdOrNotFound(companyId);
        try {
            if (companyDao.hasJobs(companyId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Company cannot be deleted because jobs are linked");
            }
            if (!companyDao.delete(companyId)) {
                throw companyNotFound(companyId);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Company cannot be deleted because jobs are linked");
        }
    }

    private CompanyResponse findByIdOrNotFound(long companyId) {
        try {
            return companyDao.findById(companyId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw companyNotFound(companyId);
        }
    }

    private static ResponseStatusException duplicateCompanyException() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A company with this name, email address, or mobile number already exists");
    }

    private static ResponseStatusException companyNotFound(long companyId) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Company " + companyId + " was not found");
    }
}
