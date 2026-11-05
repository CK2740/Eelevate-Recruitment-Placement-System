package com.recruitment.service;

import com.recruitment.dao.CandidatePortalDao;
import com.recruitment.dao.JobDao;
import com.recruitment.dto.ApplicationPageResponse;
import com.recruitment.dto.ApplicationResponse;
import com.recruitment.dto.ApplicationStatusHistoryResponse;
import com.recruitment.dto.CandidateProfileRequest;
import com.recruitment.dto.CandidateProfileResponse;
import com.recruitment.dto.CandidateNotificationResponse;
import com.recruitment.dto.CandidateResumeDownload;
import com.recruitment.dto.EmploymentType;
import com.recruitment.dto.InterviewPageResponse;
import com.recruitment.dto.JobPageResponse;
import com.recruitment.dto.JobResponse;
import com.recruitment.dto.OfferPageResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CandidatePortalService {

    private static final long MAX_RESUME_BYTES = 10L * 1024 * 1024;
    private final CandidatePortalDao portalDao;
    private final JobDao jobDao;
    private final JobApplicationService applicationService;
    private final AuditLogService auditLogService;
    private final Path resumeDirectory;

    public CandidatePortalService(
            CandidatePortalDao portalDao,
            JobDao jobDao,
            JobApplicationService applicationService,
            AuditLogService auditLogService,
            @Value("${candidate.resume.storage-dir:./data/resumes}") String resumeDirectory) {
        this.portalDao = portalDao;
        this.jobDao = jobDao;
        this.applicationService = applicationService;
        this.auditLogService = auditLogService;
        this.resumeDirectory = Path.of(resumeDirectory).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse getProfile(long userId) {
        return portalDao.findProfile(userId);
    }

    @Transactional
    public CandidateProfileResponse updateProfile(long userId, CandidateProfileRequest request) {
        portalDao.saveProfile(userId, request);
        auditLogService.log(userId, "CANDIDATE_PROFILE_UPDATED", "CANDIDATE_PROFILE", userId,
                Map.of("profile", "updated"));
        return portalDao.findProfile(userId);
    }

    @Transactional(readOnly = true)
    public JobPageResponse getAvailableJobs(
            String search,
            String jobTitle,
            String company,
            String technology,
            String location,
            EmploymentType jobType,
            Integer passingYear,
            int page,
            int size) {
        return jobDao.findAvailable(
                search, jobTitle, company, technology, location, jobType, passingYear, page, size);
    }

    @Transactional(readOnly = true)
    public JobResponse getAvailableJob(long jobId) {
        JobResponse job;
        try {
            job = jobDao.findById(jobId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job was not found");
        }
        if (job.status() != com.recruitment.dto.JobStatus.OPEN
                || job.applicationDeadline().isBefore(java.time.LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job was not found");
        }
        return job;
    }

    @Transactional
    public ApplicationResponse apply(long userId, long jobId) {
        long candidateId = portalDao.findCandidateId(userId);
        return applicationService.apply(jobId, candidateId, userId, false, false);
    }

    @Transactional(readOnly = true)
    public ApplicationPageResponse getApplications(long userId, int page, int size) {
        Long candidateId = portalDao.findCandidateIdOrNull(userId);
        return candidateId == null
                ? new ApplicationPageResponse(java.util.List.of(), page, size, 0, 0)
                : portalDao.findApplications(candidateId, page, size);
    }

    @Transactional(readOnly = true)
    public java.util.List<ApplicationStatusHistoryResponse> getApplicationHistory(
            long userId,
            long applicationId) {
        return portalDao.findApplicationHistory(portalDao.findCandidateId(userId), applicationId);
    }

    @Transactional(readOnly = true)
    public InterviewPageResponse getInterviews(long userId, int page, int size) {
        Long candidateId = portalDao.findCandidateIdOrNull(userId);
        return candidateId == null
                ? new InterviewPageResponse(java.util.List.of(), page, size, 0, 0)
                : portalDao.findInterviews(candidateId, page, size);
    }

    @Transactional(readOnly = true)
    public OfferPageResponse getOffers(long userId, int page, int size) {
        Long candidateId = portalDao.findCandidateIdOrNull(userId);
        return candidateId == null
                ? new OfferPageResponse(java.util.List.of(), page, size, 0, 0)
                : portalDao.findOffers(candidateId, page, size);
    }

    @Transactional(readOnly = true)
    public java.util.List<CandidateNotificationResponse> getNotifications(long userId) {
        Long candidateId = portalDao.findCandidateIdOrNull(userId);
        return candidateId == null
                ? java.util.List.of()
                : portalDao.findNotifications(candidateId, 20);
    }

    @Transactional
    public void uploadResume(long userId, MultipartFile resume) {
        if (resume == null || resume.isEmpty() || resume.getSize() > MAX_RESUME_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Resume must be a non-empty PDF, DOC or DOCX file no larger than 10 MB");
        }
        String originalName = safeOriginalName(resume.getOriginalFilename());
        String extension = extensionOf(originalName);
        byte[] content;
        try {
            content = resume.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded resume", exception);
        }
        if (!hasSupportedSignature(extension, content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The resume file format is not supported");
        }
        String storageKey = UUID.randomUUID() + "." + extension;
        Path storedFile = resumePath(storageKey);
        try {
            Files.createDirectories(resumeDirectory);
            try (var input = resume.getInputStream()) {
                Files.copy(input, storedFile);
            }
            String oldStorageKey = portalDao.updateResume(userId, originalName, storageKey);
            if (oldStorageKey != null) {
                deleteAfterCommit(oldStorageKey);
            }
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(storedFile);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Could not save the uploaded resume", exception);
        }
        auditLogService.log(userId, "CANDIDATE_RESUME_UPLOADED", "CANDIDATE_PROFILE", userId,
                Map.of("fileType", extension.toUpperCase(Locale.ROOT)));
    }

    @Transactional(readOnly = true)
    public CandidateResumeDownload getResume(long userId) {
        return resumeDownload(portalDao.findResume(userId));
    }

    @Transactional(readOnly = true)
    public CandidateResumeDownload getCandidateResume(long candidateId) {
        return resumeDownload(portalDao.findResumeForCandidate(candidateId));
    }

    private CandidateResumeDownload resumeDownload(
            com.recruitment.dto.CandidateResumeMetadata metadata) {
        Path storedFile = resumePath(metadata.storageKey());
        if (!Files.isRegularFile(storedFile)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume file was not found");
        }
        String extension = extensionOf(metadata.storageKey());
        String contentType = switch (extension) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
        return new CandidateResumeDownload(new FileSystemResource(storedFile), metadata.originalName(), contentType);
    }

    private Path resumePath(String storageKey) {
        if (storageKey == null || !storageKey.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(pdf|doc|docx)")) {
            throw new IllegalStateException("Stored resume key has an invalid format");
        }
        Path result = resumeDirectory.resolve(storageKey).normalize();
        if (!result.startsWith(resumeDirectory)) {
            throw new IllegalStateException("Stored resume path is outside the configured directory");
        }
        return result;
    }

    private void deleteAfterCommit(String storageKey) {
        Path previous = resumePath(storageKey);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    Files.deleteIfExists(previous);
                } catch (IOException exception) {
                    org.slf4j.LoggerFactory.getLogger(CandidatePortalService.class)
                            .warn("Could not remove replaced candidate resume after commit", exception);
                }
            }
        });
    }

    private static String safeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A resume filename is required");
        }
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\p{Cntrl}]", "_").trim();
        if (name.isBlank() || name.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The resume filename is invalid");
        }
        return name;
    }

    private static String extensionOf(String name) {
        int separator = name.lastIndexOf('.');
        if (separator < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume must be a PDF, DOC or DOCX file");
        }
        String extension = name.substring(separator + 1).toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("pdf", "doc", "docx").contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume must be a PDF, DOC or DOCX file");
        }
        return extension;
    }

    private static boolean hasSupportedSignature(String extension, byte[] content) {
        return switch (extension) {
            case "pdf" -> content.length >= 5
                    && content[0] == '%' && content[1] == 'P' && content[2] == 'D'
                    && content[3] == 'F' && content[4] == '-';
            case "doc" -> content.length >= 8
                    && (content[0] & 0xff) == 0xd0 && (content[1] & 0xff) == 0xcf
                    && (content[2] & 0xff) == 0x11 && (content[3] & 0xff) == 0xe0;
            case "docx" -> content.length >= 4
                    && content[0] == 'P' && content[1] == 'K'
                    && content[2] == 3 && content[3] == 4;
            default -> false;
        };
    }
}
