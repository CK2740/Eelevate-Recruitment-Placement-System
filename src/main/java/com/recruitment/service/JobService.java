package com.recruitment.service;

import com.recruitment.dao.JobDao;
import com.recruitment.dto.EmploymentType;
import com.recruitment.dto.JobPageResponse;
import com.recruitment.dto.JobRequest;
import com.recruitment.dto.JobResponse;
import com.recruitment.dto.JobStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobService {

    private final JobDao jobDao;

    public JobService(JobDao jobDao) {
        this.jobDao = jobDao;
    }

    @Transactional
    public JobResponse create(JobRequest request) {
        requireCompany(request.companyId());
        long jobId = jobDao.insert(request);
        return jobDao.findById(jobId);
    }

    @Transactional(readOnly = true)
    public JobResponse getById(long jobId) {
        return findByIdOrNotFound(jobId);
    }

    @Transactional(readOnly = true)
    public JobPageResponse getJobs(
            String search,
            String jobTitle,
            String company,
            String technology,
            String location,
            EmploymentType jobType,
            JobStatus status,
            Integer passingYear,
            int page,
            int size) {
        return jobDao.findAll(
                search, jobTitle, company, technology, location,
                jobType, status, passingYear, page, size);
    }

    @Transactional
    public JobResponse update(long jobId, JobRequest request) {
        findByIdOrNotFound(jobId);
        requireCompany(request.companyId());
        if (!jobDao.update(jobId, request)) {
            throw jobNotFound(jobId);
        }
        return jobDao.findById(jobId);
    }

    @Transactional
    public void delete(long jobId) {
        findByIdOrNotFound(jobId);
        try {
            if (jobDao.hasApplications(jobId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Job cannot be deleted because applications are linked");
            }
            if (!jobDao.delete(jobId)) {
                throw jobNotFound(jobId);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Job cannot be deleted because applications are linked");
        }
    }

    private void requireCompany(long companyId) {
        if (!jobDao.companyExists(companyId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Company " + companyId + " was not found");
        }
    }

    private JobResponse findByIdOrNotFound(long jobId) {
        try {
            return jobDao.findById(jobId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw jobNotFound(jobId);
        }
    }

    private static ResponseStatusException jobNotFound(long jobId) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Job " + jobId + " was not found");
    }
}
