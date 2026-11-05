package com.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.dao.InterviewDao;
import com.recruitment.dto.InterviewMode;
import com.recruitment.dto.InterviewPageResponse;
import com.recruitment.dto.InterviewRecommendation;
import com.recruitment.dto.InterviewRequest;
import com.recruitment.dto.InterviewRescheduleRequest;
import com.recruitment.dto.InterviewResponse;
import com.recruitment.dto.InterviewStatus;
import com.recruitment.dto.InterviewerFeedbackRequest;
import com.recruitment.dto.InterviewerFeedbackSubmission;
import com.recruitment.dto.InterviewerRequest;
import com.recruitment.service.InterviewService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    private static final LocalDateTime SCHEDULED_AT = LocalDateTime.of(2090, 5, 1, 10, 0);
    private static final LocalDateTime RESCHEDULED_AT = LocalDateTime.of(2090, 5, 2, 10, 0);

    @Mock
    private InterviewDao interviewDao;

    @InjectMocks
    private InterviewService interviewService;

    @Test
    void schedulesInterviewWithMultipleInterviewers() {
        when(interviewDao.lockApplication(15))
                .thenReturn(new InterviewDao.InterviewContext(15, 4, 7L));
        when(interviewDao.hasCandidateConflict(4, request().scheduledAt(), 60, null)).thenReturn(false);
        when(interviewDao.insert(request(), 7)).thenReturn(21L);
        when(interviewDao.findById(21)).thenReturn(interview(21, InterviewStatus.SCHEDULED));

        assertEquals(
                interview(21, InterviewStatus.SCHEDULED),
                interviewService.schedule(request(), 7, false, false));
        verify(interviewDao).insert(request(), 7);
    }

    @Test
    void rejectsCandidateInterviewConflict() {
        when(interviewDao.lockApplication(15))
                .thenReturn(new InterviewDao.InterviewContext(15, 4, 7L));
        when(interviewDao.hasCandidateConflict(4, request().scheduledAt(), 60, null)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> interviewService.schedule(request(), 7, false, false));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(interviewDao, never()).insert(request(), 7);
    }

    @Test
    void recruiterCannotScheduleForAnotherRecruitersApplication() {
        when(interviewDao.lockApplication(15))
                .thenReturn(new InterviewDao.InterviewContext(15, 4, 8L));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> interviewService.schedule(request(), 7, false, false));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void reschedulesWithoutConflictingWithItsCurrentSlot() {
        when(interviewDao.lockInterview(21)).thenReturn(interviewContext(InterviewStatus.SCHEDULED));
        when(interviewDao.hasCandidateConflict(4, rescheduleAt(), 45, 21L)).thenReturn(false);
        when(interviewDao.updateSchedule(21, rescheduleAt(), 45, "Room 2", null, 7)).thenReturn(true);
        when(interviewDao.findById(21)).thenReturn(interview(21, InterviewStatus.SCHEDULED));

        assertEquals(
                InterviewStatus.SCHEDULED,
                interviewService.reschedule(
                        21,
                        new InterviewRescheduleRequest(rescheduleAt(), 45, "Room 2", null),
                        7,
                        false).status());
        verify(interviewDao).hasCandidateConflict(4, rescheduleAt(), 45, 21L);
    }

    @Test
    void conflictingRescheduleIsRejected() {
        when(interviewDao.lockInterview(21)).thenReturn(interviewContext(InterviewStatus.SCHEDULED));
        when(interviewDao.hasCandidateConflict(4, rescheduleAt(), 45, 21L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> interviewService.reschedule(
                        21,
                        new InterviewRescheduleRequest(rescheduleAt(), 45, null, null),
                        7,
                        false));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(interviewDao, never()).updateSchedule(21, rescheduleAt(), 45, null, null, 7);
    }

    @Test
    void cancelsInterviewWithReasonAndPlacementOfficerCanComplete() {
        when(interviewDao.lockInterview(21)).thenReturn(interviewContext(InterviewStatus.SCHEDULED));
        when(interviewDao.updateStatus(21, InterviewStatus.SCHEDULED,
                InterviewStatus.CANCELLED, 7, "Interviewer unavailable")).thenReturn(true);
        when(interviewDao.findById(21)).thenReturn(interview(21, InterviewStatus.CANCELLED));

        assertEquals(
                InterviewStatus.CANCELLED,
                interviewService.updateStatus(
                        21,
                        InterviewStatus.CANCELLED,
                        "Interviewer unavailable",
                        7,
                        false,
                        false).status());

        when(interviewDao.lockInterview(22)).thenReturn(interviewContext(InterviewStatus.SCHEDULED));
        when(interviewDao.updateStatus(22, InterviewStatus.SCHEDULED,
                InterviewStatus.COMPLETED, 9, null)).thenReturn(true);
        when(interviewDao.findById(22)).thenReturn(interview(22, InterviewStatus.COMPLETED));

        assertEquals(
                InterviewStatus.COMPLETED,
                interviewService.updateStatus(
                        22, InterviewStatus.COMPLETED, null, 9, false, true).status());
    }

    @Test
    void rejectsCancellationWithoutReasonAndInvalidStatusChanges() {
        when(interviewDao.lockInterview(21)).thenReturn(interviewContext(InterviewStatus.SCHEDULED));

        ResponseStatusException missingReason = assertThrows(
                ResponseStatusException.class,
                () -> interviewService.updateStatus(
                        21, InterviewStatus.CANCELLED, " ", 7, false, false));
        assertEquals(HttpStatus.BAD_REQUEST, missingReason.getStatusCode());

        ResponseStatusException forbiddenCancellation = assertThrows(
                ResponseStatusException.class,
                () -> interviewService.updateStatus(
                        21, InterviewStatus.CANCELLED, "Reason", 9, false, true));
        assertEquals(HttpStatus.CONFLICT, forbiddenCancellation.getStatusCode());
    }

    @Test
    void interviewerFeedbackCanBeRecordedOnlyForCompletedInterviews() {
        when(interviewDao.lockInterview(21)).thenReturn(interviewContext(InterviewStatus.COMPLETED));
        when(interviewDao.saveFeedback(21, 31, feedback())).thenReturn(true);
        when(interviewDao.findById(21)).thenReturn(interview(21, InterviewStatus.COMPLETED));

        assertEquals(
                InterviewStatus.COMPLETED,
                interviewService.submitFeedback(
                        21,
                        List.of(new InterviewerFeedbackSubmission(31L, feedback())),
                        7,
                        false,
                        false).status());

        when(interviewDao.lockInterview(22)).thenReturn(interviewContext(InterviewStatus.SCHEDULED));
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> interviewService.submitFeedback(
                        22,
                        List.of(new InterviewerFeedbackSubmission(31L, feedback())),
                        7,
                        false,
                        false));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void listsAndViewsInterviewsWithinRoleScope() {
        InterviewPageResponse expected = new InterviewPageResponse(
                List.of(interview(21, InterviewStatus.SCHEDULED)), 0, 10, 1, 1);
        when(interviewDao.findAll(
                null, "Ada", "Engineer", "Acme", InterviewStatus.SCHEDULED,
                InterviewMode.VIDEO, null, null, 7L, 0, 10))
                .thenReturn(expected);
        when(interviewDao.findById(21)).thenReturn(interview(21, InterviewStatus.SCHEDULED));
        when(interviewDao.recruiterOwnsInterview(21, 7)).thenReturn(true);

        assertEquals(expected, interviewService.getInterviews(
                null, "Ada", "Engineer", "Acme", InterviewStatus.SCHEDULED,
                InterviewMode.VIDEO, null, null, 7, true, 0, 10));
        assertEquals(
                interview(21, InterviewStatus.SCHEDULED),
                interviewService.getById(21, 7, false, false));
    }

    @Test
    void missingApplicationReturnsNotFoundWhenScheduling() {
        when(interviewDao.lockApplication(999)).thenThrow(new EmptyResultDataAccessException(1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> interviewService.schedule(requestFor(999), 7, true, false));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private static InterviewDao.InterviewContext interviewContext(InterviewStatus status) {
        return new InterviewDao.InterviewContext(15, 4, 7L, status, 21);
    }

    private static InterviewRequest request() {
        return requestFor(15);
    }

    private static InterviewRequest requestFor(long applicationId) {
        return new InterviewRequest(
                applicationId,
                "Technical",
                "TECHNICAL",
                1,
                SCHEDULED_AT,
                60,
                InterviewMode.VIDEO,
                null,
                "https://meeting.example/interview",
                List.of(
                        new InterviewerRequest("Grace Hopper", "grace@example.com"),
                        new InterviewerRequest("Alan Turing", "alan@example.com")));
    }

    private static LocalDateTime rescheduleAt() {
        return RESCHEDULED_AT;
    }

    private static InterviewerFeedbackRequest feedback() {
        return new InterviewerFeedbackRequest(
                new BigDecimal("8.5"),
                new BigDecimal("9.0"),
                InterviewRecommendation.PASS,
                "Strong technical skills");
    }

    private static InterviewResponse interview(long id, InterviewStatus status) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new InterviewResponse(
                id,
                15,
                4,
                "Ada Lovelace",
                8,
                "Software Engineer",
                "Acme Inc",
                "Technical",
                "TECHNICAL",
                1,
                SCHEDULED_AT,
                60,
                InterviewMode.VIDEO,
                null,
                "https://meeting.example/interview",
                status,
                List.of(),
                timestamp,
                timestamp);
    }
}
