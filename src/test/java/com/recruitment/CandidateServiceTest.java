package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.dao.CandidateDao;
import com.recruitment.dto.CandidateResponse;
import com.recruitment.dto.CandidateStatus;
import com.recruitment.service.CandidateService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock
    private CandidateDao candidateDao;

    @InjectMocks
    private CandidateService candidateService;

    @Test
    void duplicateCandidateEmailOrMobileReturnsConflict() {
        when(candidateDao.insert(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DuplicateKeyException("duplicate"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> candidateService.create(null));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void candidateWithApplicationsCannotBeDeleted() {
        when(candidateDao.findById(5)).thenReturn(candidate(5));
        when(candidateDao.hasApplications(5)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> candidateService.delete(5));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(candidateDao, never()).delete(5);
    }

    @Test
    void missingCandidateReturnsNotFound() {
        when(candidateDao.findById(999)).thenThrow(new EmptyResultDataAccessException(1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> candidateService.getById(999));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private static CandidateResponse candidate(long id) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new CandidateResponse(
                id,
                "Ada Lovelace",
                "candidate@example.com",
                "+15551234567",
                "BSc Computer Science",
                2025,
                new BigDecimal("92.50"),
                0,
                "Java, SQL",
                CandidateStatus.ACTIVE,
                timestamp,
                timestamp);
    }
}
