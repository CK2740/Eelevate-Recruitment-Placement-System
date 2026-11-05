package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.dao.CompanyDao;
import com.recruitment.dto.CompanyPageResponse;
import com.recruitment.dto.CompanyRequest;
import com.recruitment.dto.CompanyResponse;
import com.recruitment.dto.CompanyStatus;
import com.recruitment.service.CompanyService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyDao companyDao;

    @InjectMocks
    private CompanyService companyService;

    @Test
    void createsCompanyAndReturnsPersistedRecord() {
        when(companyDao.insert(any(CompanyRequest.class))).thenReturn(5L);
        when(companyDao.findById(5)).thenReturn(company(5));

        assertEquals(company(5), companyService.create(request()));
        verify(companyDao).insert(request());
    }

    @Test
    void duplicateNameEmailOrMobileReturnsConflict() {
        when(companyDao.insert(any(CompanyRequest.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> companyService.create(request()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void duplicateNameEmailOrMobileOnUpdateReturnsConflict() {
        when(companyDao.findById(5)).thenReturn(company(5));
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(companyDao).update(5, request());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> companyService.update(5, request()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void readsAndPagesCompaniesWithRequestedFilters() {
        CompanyPageResponse expected = new CompanyPageResponse(List.of(company(5)), 1, 10, 11, 2);
        when(companyDao.findAll("software", "Acme", "Technology", "London",
                CompanyStatus.ACTIVE, 1, 10)).thenReturn(expected);

        assertEquals(expected, companyService.getCompanies(
                "software", "Acme", "Technology", "London", CompanyStatus.ACTIVE, 1, 10));
        when(companyDao.findById(5)).thenReturn(company(5));
        assertEquals(company(5), companyService.getById(5));
    }

    @Test
    void updatesCompanyAndReturnsUpdatedRecord() {
        when(companyDao.findById(5)).thenReturn(company(5));

        assertEquals(company(5), companyService.update(5, request()));
        verify(companyDao).update(5, request());
    }

    @Test
    void deletesCompanyWhenNoJobsAreLinked() {
        when(companyDao.findById(5)).thenReturn(company(5));
        when(companyDao.delete(5)).thenReturn(true);

        companyService.delete(5);

        verify(companyDao).delete(5);
    }

    @Test
    void companyWithJobsCannotBeDeleted() {
        when(companyDao.findById(5)).thenReturn(company(5));
        when(companyDao.hasJobs(5)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> companyService.delete(5));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(companyDao, never()).delete(5);
    }

    @Test
    void foreignKeyRestrictionPreventsDeleteIfJobIsAddedConcurrently() {
        when(companyDao.findById(5)).thenReturn(company(5));
        when(companyDao.delete(5)).thenThrow(new DataIntegrityViolationException("foreign key"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> companyService.delete(5));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void missingCompanyReturnsNotFound() {
        when(companyDao.findById(999)).thenThrow(new EmptyResultDataAccessException(1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> companyService.getById(999));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private static CompanyRequest request() {
        return new CompanyRequest(
                "Acme Inc",
                "Technology",
                "London",
                "Ada Lovelace",
                "contact@acme.example",
                "+441234567890",
                "https://acme.example",
                CompanyStatus.ACTIVE);
    }

    private static CompanyResponse company(long id) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new CompanyResponse(
                id,
                "Acme Inc",
                "Technology",
                "London",
                "Ada Lovelace",
                "contact@acme.example",
                "+441234567890",
                "https://acme.example",
                CompanyStatus.ACTIVE,
                timestamp,
                timestamp);
    }
}
