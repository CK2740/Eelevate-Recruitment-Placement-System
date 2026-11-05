package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.dao.JobDao;
import com.recruitment.dto.EmploymentType;
import com.recruitment.dto.JobEligibilityRequest;
import com.recruitment.dto.JobPageResponse;
import com.recruitment.dto.JobRequest;
import com.recruitment.dto.JobResponse;
import com.recruitment.dto.JobStatus;
import com.recruitment.service.JobService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobDao jobDao;

    @InjectMocks
    private JobService jobService;

    @Test
    void createsJobLinkedToExistingCompany() {
        when(jobDao.companyExists(3)).thenReturn(true);
        when(jobDao.insert(any(JobRequest.class))).thenReturn(8L);
        when(jobDao.findById(8)).thenReturn(job(8));

        assertEquals(job(8), jobService.create(request()));
        verify(jobDao).insert(request());
    }

    @Test
    void cannotCreateJobForMissingCompany() {
        when(jobDao.companyExists(3)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> jobService.create(request()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(jobDao, never()).insert(any());
    }

    @Test
    void readsAndPagesJobsUsingSearchAndFilters() {
        JobPageResponse expected = new JobPageResponse(List.of(job(8)), 1, 10, 11, 2);
        when(jobDao.findAll("Java", "Engineer", "Acme", "Java", "London",
                EmploymentType.FULL_TIME, JobStatus.OPEN, 2025, 1, 10))
                .thenReturn(expected);
        when(jobDao.findById(8)).thenReturn(job(8));

        assertEquals(expected, jobService.getJobs(
                "Java", "Engineer", "Acme", "Java", "London",
                EmploymentType.FULL_TIME, JobStatus.OPEN, 2025, 1, 10));
        assertEquals(job(8), jobService.getById(8));
    }

    @Test
    void updatesJobAndEligibility() {
        when(jobDao.findById(8)).thenReturn(job(8));
        when(jobDao.companyExists(3)).thenReturn(true);
        when(jobDao.update(8, request())).thenReturn(true);

        assertEquals(job(8), jobService.update(8, request()));
        verify(jobDao).update(8, request());
    }

    @Test
    void deletesJobWhenNoApplicationsAreLinked() {
        when(jobDao.findById(8)).thenReturn(job(8));
        when(jobDao.delete(8)).thenReturn(true);

        jobService.delete(8);

        verify(jobDao).hasApplications(8);
        verify(jobDao).delete(8);
    }

    @Test
    void jobWithApplicationsCannotBeDeleted() {
        when(jobDao.findById(8)).thenReturn(job(8));
        when(jobDao.hasApplications(8)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> jobService.delete(8));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(jobDao, never()).delete(8);
    }

    @Test
    void databaseForeignKeyPreventsDeleteIfApplicationIsAddedConcurrently() {
        when(jobDao.findById(8)).thenReturn(job(8));
        when(jobDao.delete(8)).thenThrow(new DataIntegrityViolationException("foreign key"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> jobService.delete(8));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void missingJobReturnsNotFound() {
        when(jobDao.findById(999)).thenThrow(new EmptyResultDataAccessException(1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> jobService.getById(999));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private static JobRequest request() {
        return new JobRequest(
                3L,
                "Software Engineer",
                "Java",
                4,
                "London",
                new BigDecimal("85000.00"),
                "Build backend systems",
                "Java, SQL",
                LocalDate.now().plusDays(30),
                EmploymentType.FULL_TIME,
                JobStatus.OPEN,
                new JobEligibilityRequest(new BigDecimal("70.00"), 2023, 2026, 1));
    }

    private static JobResponse job(long id) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new JobResponse(
                id,
                3,
                "Acme Inc",
                "Software Engineer",
                "Java",
                4,
                "London",
                new BigDecimal("85000.00"),
                "Build backend systems",
                "Java, SQL",
                LocalDate.of(2026, 12, 1),
                EmploymentType.FULL_TIME,
                JobStatus.OPEN,
                new JobEligibilityRequest(new BigDecimal("70.00"), 2023, 2026, 1),
                timestamp,
                timestamp);
    }
}
