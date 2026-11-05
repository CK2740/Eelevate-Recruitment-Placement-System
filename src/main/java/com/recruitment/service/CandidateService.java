package com.recruitment.service;

import com.recruitment.dao.CandidateDao;
import com.recruitment.dto.CandidatePageResponse;
import com.recruitment.dto.CandidateRequest;
import com.recruitment.dto.CandidateResponse;
import com.recruitment.dto.CandidateStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CandidateService {

    private final CandidateDao candidateDao;

    public CandidateService(CandidateDao candidateDao) {
        this.candidateDao = candidateDao;
    }

    @Transactional
    public CandidateResponse create(CandidateRequest request) {
        try {
            long candidateId = candidateDao.insert(request);
            return candidateDao.findById(candidateId);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A candidate with this email address or mobile number already exists");
        }
    }

    @Transactional(readOnly = true)
    public CandidateResponse getById(long candidateId) {
        return findByIdOrNotFound(candidateId);
    }

    @Transactional(readOnly = true)
    public CandidatePageResponse getCandidates(
            String search,
            String name,
            String email,
            String technology,
            Integer passingYear,
            CandidateStatus status,
            int page,
            int size) {
        return candidateDao.findAll(
                search,
                name,
                email,
                technology,
                passingYear,
                status,
                page,
                size);
    }

    @Transactional
    public CandidateResponse update(long candidateId, CandidateRequest request) {
        try {
            candidateDao.update(candidateId, request);
            return candidateDao.findById(candidateId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw candidateNotFound(candidateId);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A candidate with this email address or mobile number already exists");
        }
    }

    @Transactional
    public CandidateResponse updateStatus(long candidateId, CandidateStatus status) {
        try {
            candidateDao.updateStatus(candidateId, status);
            return candidateDao.findById(candidateId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw candidateNotFound(candidateId);
        }
    }

    @Transactional
    public void delete(long candidateId) {
        findByIdOrNotFound(candidateId);
        try {
            if (candidateDao.hasApplications(candidateId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Candidate cannot be deleted because applications are linked");
            }
            if (!candidateDao.delete(candidateId)) {
                throw candidateNotFound(candidateId);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Candidate cannot be deleted because related records are linked");
        }
    }

    private CandidateResponse findByIdOrNotFound(long candidateId) {
        try {
            return candidateDao.findById(candidateId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw candidateNotFound(candidateId);
        }
    }

    private static ResponseStatusException candidateNotFound(long candidateId) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Candidate " + candidateId + " was not found");
    }
}
