package com.recruitment.service;

import com.recruitment.dao.ApplicationEligibilityContext;
import com.recruitment.dao.JobApplicationDao;
import com.recruitment.dto.ApplicationPageResponse;
import com.recruitment.dto.ApplicationResponse;
import com.recruitment.dto.ApplicationStatus;
import com.recruitment.dto.ApplicationStatusHistoryResponse;
import com.recruitment.dto.ApplicationStatusRequest;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobApplicationService {

    private final JobApplicationDao applicationDao;

    public JobApplicationService(JobApplicationDao applicationDao) {
        this.applicationDao = applicationDao;
    }

    @Transactional
    public ApplicationResponse apply(
            long jobId,
            long candidateId,
            long userId,
            boolean administrator,
            boolean recruiter) {
        ApplicationEligibilityContext context;
        try {
            context = applicationDao.findEligibilityContext(jobId, candidateId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Job or candidate was not found");
        }
        if (recruiter && !administrator) {
            requireAssignedRecruiter(context.assignedRecruiterId(), userId);
        }
        if (!"OPEN".equals(context.jobStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Applications are not accepted for a job that is not open");
        }
        if (context.applicationDeadline().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The application deadline has passed");
        }
        if (!context.candidateActive()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Inactive candidates cannot apply");
        }
        requireEligible(context);
        try {
            long applicationId = applicationDao.insert(candidateId, jobId, userId);
            return applicationDao.findById(applicationId);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This candidate has already applied to the job");
        }
    }

    @Transactional(readOnly = true)
    public ApplicationPageResponse getApplications(
            String search,
            String candidate,
            String company,
            String job,
            ApplicationStatus status,
            Integer passingYear,
            long userId,
            boolean recruiter,
            int page,
            int size) {
        return applicationDao.findAll(
                search,
                candidate,
                company,
                job,
                status,
                passingYear,
                recruiter ? userId : null,
                page,
                size);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getById(
            long applicationId,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        ApplicationResponse application = findByIdOrNotFound(applicationId);
        if (!administrator && !placementOfficer) {
            requireRecruiterAccess(applicationId, userId);
        }
        return application;
    }

    @Transactional(readOnly = true)
    public List<ApplicationStatusHistoryResponse> getHistory(
            long applicationId,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        if (!administrator && !placementOfficer) {
            requireRecruiterAccess(applicationId, userId);
        }
        findByIdOrNotFound(applicationId);
        return applicationDao.findHistory(applicationId);
    }

    @Transactional
    public ApplicationResponse updateStatus(
            long applicationId,
            ApplicationStatusRequest request,
            long userId,
            boolean administrator,
            boolean placementOfficer) {
        JobApplicationDao.ApplicationState state;
        try {
            state = applicationDao.lockState(applicationId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw applicationNotFound(applicationId);
        }
        if (!administrator && !placementOfficer) {
            requireAssignedRecruiter(state.assignedRecruiterId(), userId);
        }
        if (!isValidTransition(state.status(), request.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid application status transition from " + state.status()
                            + " to " + request.status());
        }
        if (requiresReason(request.status()) && !hasText(request.reason())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A reason is required when shortlisting or rejecting a candidate");
        }
        applicationDao.updateStatus(
                applicationId,
                state.status(),
                request.status(),
                userId,
                request.reason());
        return applicationDao.findById(applicationId);
    }

    private void requireRecruiterAccess(long applicationId, long userId) {
        Long assignedRecruiterId;
        try {
            assignedRecruiterId = applicationDao.findAssignedRecruiterId(applicationId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw applicationNotFound(applicationId);
        }
        requireAssignedRecruiter(assignedRecruiterId, userId);
    }

    private static void requireAssignedRecruiter(Long assignedRecruiterId, long userId) {
        if (assignedRecruiterId == null || assignedRecruiterId != userId) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Recruiters can manage applications only for their assigned jobs");
        }
    }

    private ApplicationResponse findByIdOrNotFound(long applicationId) {
        try {
            return applicationDao.findById(applicationId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw applicationNotFound(applicationId);
        }
    }

    private static void requireEligible(ApplicationEligibilityContext context) {
        if (context.passingYear() < valueOrDefault(context.minimumPassingYear(), Integer.MIN_VALUE)
                || context.passingYear() > valueOrDefault(context.maximumPassingYear(), Integer.MAX_VALUE)) {
            throw ineligible("Candidate passing year does not meet the job criteria");
        }
        if (context.percentage().compareTo(context.minimumPercentage()) < 0) {
            throw ineligible("Candidate percentage does not meet the job criteria");
        }
        if (context.backlogCount() > context.maximumBacklogs()) {
            throw ineligible("Candidate backlog count exceeds the job criteria");
        }
        if (hasText(context.requiredQualification())
                && (context.qualification() == null
                || !context.qualification().toLowerCase(Locale.ROOT)
                        .contains(context.requiredQualification().trim().toLowerCase(Locale.ROOT)))) {
            throw ineligible("Candidate qualification does not meet the job criteria");
        }
        if (hasText(context.requiredSkills())) {
            Set<String> candidateSkills = context.candidateSkills() == null
                    ? Set.of()
                    : Arrays.stream(context.candidateSkills().split("[,;]"))
                            .map(skill -> skill.trim().toLowerCase(Locale.ROOT))
                            .collect(Collectors.toSet());
            boolean hasAllRequiredSkills = Arrays.stream(context.requiredSkills().split("[,;]"))
                    .map(String::trim)
                    .filter(JobApplicationService::hasText)
                    .map(skill -> skill.toLowerCase(Locale.ROOT))
                    .allMatch(candidateSkills::contains);
            if (!hasAllRequiredSkills) {
                throw ineligible("Candidate skills do not meet the job criteria");
            }
        }
    }

    private static int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static boolean isValidTransition(ApplicationStatus current, ApplicationStatus next) {
        return switch (current) {
            case APPLIED -> Set.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED).contains(next);
            case SHORTLISTED -> Set.of(ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED).contains(next);
            case INTERVIEW -> Set.of(ApplicationStatus.SELECTED, ApplicationStatus.REJECTED).contains(next);
            case SELECTED -> Set.of(ApplicationStatus.OFFERED, ApplicationStatus.REJECTED).contains(next);
            case OFFERED -> Set.of(ApplicationStatus.JOINED, ApplicationStatus.REJECTED).contains(next);
            case JOINED, REJECTED -> false;
        };
    }

    private static boolean requiresReason(ApplicationStatus status) {
        return status == ApplicationStatus.SHORTLISTED || status == ApplicationStatus.REJECTED;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ResponseStatusException ineligible(String reason) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
    }

    private static ResponseStatusException applicationNotFound(long applicationId) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Application " + applicationId + " was not found");
    }
}
