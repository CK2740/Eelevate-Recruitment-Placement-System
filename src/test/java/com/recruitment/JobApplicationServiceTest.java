package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.dao.ApplicationEligibilityContext;
import com.recruitment.dao.JobApplicationDao;
import com.recruitment.dto.ApplicationResponse;
import com.recruitment.dto.ApplicationStatus;
import com.recruitment.dto.ApplicationStatusRequest;
import com.recruitment.service.JobApplicationService;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class JobApplicationServiceTest {

    @Mock
    private JobApplicationDao applicationDao;

    @InjectMocks
    private JobApplicationService applicationService;

    @Test
    void eligibleCandidateCanApplyAndApplicationHistoryIsCreated() {
        when(applicationDao.findEligibilityContext(8, 4)).thenReturn(eligibleContext());
        when(applicationDao.insert(4, 8, 7)).thenReturn(12L);
        when(applicationDao.findById(12)).thenReturn(application(12, ApplicationStatus.APPLIED));

        assertEquals(
                application(12, ApplicationStatus.APPLIED),
                applicationService.apply(8, 4, 7, false, true));

        verify(applicationDao).insert(4, 8, 7);
    }

    @Test
    void rejectsCandidateWhoFailsPassingYearPercentageBacklogsQualificationOrSkills() {
        assertIneligible(new ApplicationEligibilityContext(
                4, true, "BSc Computer Science", 2021, new BigDecimal("80"),
                0, "Java, SQL", 8, 3, 7L, "OPEN", LocalDate.now().plusDays(1),
                "Computer Science", "Java, SQL", new BigDecimal("70"), 2022, 2026, 1));
        assertIneligible(new ApplicationEligibilityContext(
                4, true, "BSc Computer Science", 2024, new BigDecimal("60"),
                0, "Java, SQL", 8, 3, 7L, "OPEN", LocalDate.now().plusDays(1),
                "Computer Science", "Java, SQL", new BigDecimal("70"), 2022, 2026, 1));
        assertIneligible(new ApplicationEligibilityContext(
                4, true, "BSc Computer Science", 2024, new BigDecimal("80"),
                2, "Java, SQL", 8, 3, 7L, "OPEN", LocalDate.now().plusDays(1),
                "Computer Science", "Java, SQL", new BigDecimal("70"), 2022, 2026, 1));
        assertIneligible(new ApplicationEligibilityContext(
                4, true, "BA English", 2024, new BigDecimal("80"),
                0, "Java, SQL", 8, 3, 7L, "OPEN", LocalDate.now().plusDays(1),
                "Computer Science", "Java, SQL", new BigDecimal("70"), 2022, 2026, 1));
        assertIneligible(new ApplicationEligibilityContext(
                4, true, "BSc Computer Science", 2024, new BigDecimal("80"),
                0, "JavaScript, SQL", 8, 3, 7L, "OPEN", LocalDate.now().plusDays(1),
                "Computer Science", "Java, SQL", new BigDecimal("70"), 2022, 2026, 1));
    }

    @Test
    void cannotApplyAfterDeadlineOrToClosedJob() {
        when(applicationDao.findEligibilityContext(8, 4)).thenReturn(new ApplicationEligibilityContext(
                4, true, "BSc Computer Science", 2024, new BigDecimal("80"),
                0, "Java, SQL", 8, 3, 7L, "OPEN", LocalDate.now().minusDays(1),
                "Computer Science", "Java, SQL", new BigDecimal("70"), 2022, 2026, 1));
        ResponseStatusException deadlineException = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.apply(8, 4, 7, false, true));
        assertEquals(HttpStatus.CONFLICT, deadlineException.getStatusCode());

        when(applicationDao.findEligibilityContext(8, 4)).thenReturn(new ApplicationEligibilityContext(
                4, true, "BSc Computer Science", 2024, new BigDecimal("80"),
                0, "Java, SQL", 8, 3, 7L, "CLOSED", LocalDate.now().plusDays(1),
                "Computer Science", "Java, SQL", new BigDecimal("70"), 2022, 2026, 1));
        ResponseStatusException closedJobException = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.apply(8, 4, 7, false, true));
        assertEquals(HttpStatus.CONFLICT, closedJobException.getStatusCode());
        verify(applicationDao, never()).insert(4, 8, 7);
    }

    @Test
    void preventsDuplicateCandidateJobApplication() {
        when(applicationDao.findEligibilityContext(8, 4)).thenReturn(eligibleContext());
        when(applicationDao.insert(4, 8, 7)).thenThrow(new DuplicateKeyException("duplicate"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.apply(8, 4, 7, false, true));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void recruiterCanManageOnlyApplicationsForAssignedJobs() {
        when(applicationDao.lockState(12))
                .thenReturn(new JobApplicationDao.ApplicationState(ApplicationStatus.APPLIED, 7L));
        when(applicationDao.findById(12)).thenReturn(application(12, ApplicationStatus.SHORTLISTED));

        ApplicationResponse response = applicationService.updateStatus(
                12,
                new ApplicationStatusRequest(ApplicationStatus.SHORTLISTED, "Strong match"),
                7,
                false,
                false);

        assertEquals(ApplicationStatus.SHORTLISTED, response.status());
        verify(applicationDao).updateStatus(
                12, ApplicationStatus.APPLIED, ApplicationStatus.SHORTLISTED, 7, "Strong match");

        when(applicationDao.lockState(12))
                .thenReturn(new JobApplicationDao.ApplicationState(ApplicationStatus.APPLIED, 8L));
        ResponseStatusException denied = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.updateStatus(
                        12,
                        new ApplicationStatusRequest(ApplicationStatus.SHORTLISTED, "Strong match"),
                        7,
                        false,
                        false));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
    }

    @Test
    void requiresShortlistAndRejectionReasonsAndValidTransitions() {
        when(applicationDao.lockState(12))
                .thenReturn(new JobApplicationDao.ApplicationState(ApplicationStatus.APPLIED, 7L));

        ResponseStatusException missingReason = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.updateStatus(
                        12,
                        new ApplicationStatusRequest(ApplicationStatus.SHORTLISTED, " "),
                        7,
                        false,
                        false));
        assertEquals(HttpStatus.BAD_REQUEST, missingReason.getStatusCode());

        ResponseStatusException invalidTransition = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.updateStatus(
                        12,
                        new ApplicationStatusRequest(ApplicationStatus.INTERVIEW, null),
                        7,
                        false,
                        false));
        assertEquals(HttpStatus.CONFLICT, invalidTransition.getStatusCode());
        verify(applicationDao, never()).updateStatus(
                12, ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW, 7, null);
    }

    @Test
    void onlyPlacementOfficerOrAdminMayManageApplicationsOutsideRecruiterAssignment() {
        when(applicationDao.lockState(12))
                .thenReturn(new JobApplicationDao.ApplicationState(ApplicationStatus.APPLIED, 8L));
        when(applicationDao.findById(12)).thenReturn(application(12, ApplicationStatus.REJECTED));

        assertEquals(
                ApplicationStatus.REJECTED,
                applicationService.updateStatus(
                        12,
                        new ApplicationStatusRequest(ApplicationStatus.REJECTED, "Insufficient experience"),
                        7,
                        false,
                        true).status());
    }

    @Test
    void missingApplicationReturnsNotFound() {
        when(applicationDao.lockState(999)).thenThrow(new EmptyResultDataAccessException(1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.updateStatus(
                        999,
                        new ApplicationStatusRequest(ApplicationStatus.REJECTED, "Reason"),
                        7,
                        true,
                        false));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private void assertIneligible(ApplicationEligibilityContext context) {
        when(applicationDao.findEligibilityContext(8, 4)).thenReturn(context);
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.apply(8, 4, 7, false, true));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
        verify(applicationDao, never()).insert(4, 8, 7);
    }

    private static ApplicationEligibilityContext eligibleContext() {
        return new ApplicationEligibilityContext(
                4,
                true,
                "BSc Computer Science",
                2024,
                new BigDecimal("80"),
                0,
                "Java, SQL",
                8,
                3,
                7L,
                "OPEN",
                LocalDate.now().plusDays(1),
                "Computer Science",
                "Java, SQL",
                new BigDecimal("70"),
                2022,
                2026,
                1);
    }

    private static ApplicationResponse application(long id, ApplicationStatus status) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new ApplicationResponse(
                id,
                4,
                "Ada Lovelace",
                8,
                "Software Engineer",
                3,
                "Acme Inc",
                status,
                status == ApplicationStatus.APPLIED ? "Application submitted" : "Strong match",
                timestamp,
                timestamp);
    }
}
