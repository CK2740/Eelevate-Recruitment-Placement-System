package com.recruitment.service;

import com.recruitment.dao.InterviewDao;
import com.recruitment.dto.InterviewPageResponse;
import com.recruitment.dto.InterviewRequest;
import com.recruitment.dto.InterviewRescheduleRequest;
import com.recruitment.dto.InterviewResponse;
import com.recruitment.dto.InterviewStatus;
import com.recruitment.dto.InterviewStatusHistoryResponse;
import com.recruitment.dto.InterviewerFeedbackSubmission;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InterviewService {

    private final InterviewDao interviewDao;

    public InterviewService(InterviewDao interviewDao) {
        this.interviewDao = interviewDao;
    }

    @Transactional
    public InterviewResponse schedule(
            InterviewRequest request,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        InterviewDao.InterviewContext context = lockApplication(request.applicationId());
        requireRecruiterOrAdmin(context.assignedRecruiterId(), userId, administrator, placementOfficer);
        ensureNoConflict(
                context.candidateId(),
                request.scheduledAt(),
                request.durationMinutes(),
                null);
        long interviewId = interviewDao.insert(request, userId);
        return interviewDao.findById(interviewId);
    }

    @Transactional
    public InterviewResponse reschedule(
            long interviewId,
            InterviewRescheduleRequest request,
            long userId,
            boolean administrator) {
        InterviewDao.InterviewContext context = lockInterview(interviewId);
        requireRecruiter(context.assignedRecruiterId(), userId, administrator);
        if (context.status() != InterviewStatus.SCHEDULED) {
            throw conflict("Only scheduled interviews can be rescheduled");
        }
        InterviewResponse existing = interviewDao.findById(interviewId);
        LocalDateTime scheduledAt = request.scheduledAt();
        int duration = java.util.Optional.ofNullable(request.durationMinutes())
                .map(Integer::intValue)
                .orElseGet(() -> existing.durationMinutes());
        ensureNoConflict(context.candidateId(), scheduledAt, duration, interviewId);
        if (!interviewDao.updateSchedule(
                interviewId,
                scheduledAt,
                request.durationMinutes(),
                request.location(),
                request.meetingLink(),
                userId)) {
            throw interviewNotFound(interviewId);
        }
        return interviewDao.findById(interviewId);
    }

    @Transactional
    public InterviewResponse updateStatus(
            long interviewId,
            InterviewStatus status,
            String reason,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        InterviewDao.InterviewContext context = lockInterview(interviewId);
        requireRecruiterOrAdmin(context.assignedRecruiterId(), userId, administrator, placementOfficer);
        InterviewStatus previous = context.status();
        if (!isValidTransition(previous, status, false)) {
            throw conflict("Invalid interview status transition from " + previous + " to " + status);
        }
        if (status == InterviewStatus.CANCELLED && !hasText(reason)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A reason is required when cancelling an interview");
        }
        if (!interviewDao.updateStatus(interviewId, previous, status, userId, reason)) {
            throw conflict("Interview status changed before the request could be completed");
        }
        return interviewDao.findById(interviewId);
    }

    @Transactional
    public InterviewResponse submitFeedback(
            long interviewId,
            List<InterviewerFeedbackSubmission> submissions,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        InterviewDao.InterviewContext context = lockInterview(interviewId);
        requireRecruiterOrAdmin(context.assignedRecruiterId(), userId, administrator, placementOfficer);
        if (context.status() != InterviewStatus.COMPLETED) {
            throw conflict("Feedback can be submitted only for completed interviews");
        }
        for (InterviewerFeedbackSubmission submission : submissions) {
            if (!interviewDao.saveFeedback(
                    interviewId,
                    submission.interviewerId(),
                    submission.feedback())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Interviewer " + submission.interviewerId()
                                + " is not assigned to interview " + interviewId);
            }
        }
        return interviewDao.findById(interviewId);
    }

    @Transactional(readOnly = true)
    public InterviewResponse getById(
            long interviewId,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        InterviewResponse interview = findByIdOrNotFound(interviewId);
        requireReadAccess(interviewId, userId, administrator, placementOfficer);
        return interview;
    }

    @Transactional(readOnly = true)
    public InterviewPageResponse getInterviews(
            String search,
            String candidate,
            String job,
            String company,
            InterviewStatus status,
            com.recruitment.dto.InterviewMode mode,
            LocalDateTime from,
            LocalDateTime to,
            long userId,
            boolean recruiter,
            int page,
            int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The from date/time must not be after the to date/time");
        }
        return interviewDao.findAll(
                search,
                candidate,
                job,
                company,
                status,
                mode,
                from,
                to,
                recruiter ? userId : null,
                page,
                size);
    }

    @Transactional(readOnly = true)
    public List<InterviewStatusHistoryResponse> getHistory(
            long interviewId,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        findByIdOrNotFound(interviewId);
        requireReadAccess(interviewId, userId, administrator, placementOfficer);
        return interviewDao.findHistory(interviewId);
    }

    private void ensureNoConflict(
            long candidateId,
            LocalDateTime scheduledAt,
            int durationMinutes,
            Long excludedInterviewId) {
        if (interviewDao.hasCandidateConflict(
                candidateId,
                scheduledAt,
                durationMinutes,
                excludedInterviewId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The candidate already has an interview scheduled during this time");
        }
    }

    private InterviewDao.InterviewContext lockApplication(long applicationId) {
        try {
            return interviewDao.lockApplication(applicationId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Application " + applicationId + " was not found");
        }
    }

    private InterviewDao.InterviewContext lockInterview(long interviewId) {
        try {
            return interviewDao.lockInterview(interviewId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw interviewNotFound(interviewId);
        }
    }

    private void requireReadAccess(
            long interviewId,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        if (!administrator && !placementOfficer && !interviewDao.recruiterOwnsInterview(interviewId, userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Recruiters can access interviews only for their assigned jobs");
        }
    }

    private static void requireRecruiterOrAdmin(
            Long assignedRecruiterId,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        if (!administrator && !placementOfficer) {
            requireRecruiter(assignedRecruiterId, userId, false);
        }
    }

    private static void requireRecruiter(
            Long assignedRecruiterId,
            long userId,
            boolean administrator) {
        if (!administrator && (assignedRecruiterId == null || assignedRecruiterId != userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Recruiters can manage interviews only for their assigned jobs");
        }
    }

    private static boolean isValidTransition(
            InterviewStatus current,
            InterviewStatus next,
            boolean placementOfficer) {
        if (current != InterviewStatus.SCHEDULED) {
            return false;
        }
        return switch (next) {
            case CANCELLED -> !placementOfficer;
            case COMPLETED, NO_SHOW -> true;
            case SCHEDULED -> false;
        };
    }

    private InterviewResponse findByIdOrNotFound(long interviewId) {
        try {
            return interviewDao.findById(interviewId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw interviewNotFound(interviewId);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException interviewNotFound(long interviewId) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Interview " + interviewId + " was not found");
    }
}
