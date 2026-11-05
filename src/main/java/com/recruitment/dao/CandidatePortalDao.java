package com.recruitment.dao;

import com.recruitment.dto.ApplicationPageResponse;
import com.recruitment.dto.CandidateProfileRequest;
import com.recruitment.dto.CandidateProfileResponse;
import com.recruitment.dto.CandidateNotificationResponse;
import com.recruitment.dto.CandidateResumeMetadata;
import com.recruitment.dto.InterviewPageResponse;
import com.recruitment.dto.OfferPageResponse;
import java.sql.Statement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Repository
public class CandidatePortalDao {

    private static final String PROFILE_QUERY = """
            SELECT u.full_name, u.email, p.mobile, p.qualification, p.passing_year,
                   p.percentage, p.backlog_count, p.skills, p.experience,
                   p.resume_original_name,
                   (p.mobile IS NOT NULL AND p.qualification IS NOT NULL
                    AND p.passing_year IS NOT NULL AND p.percentage IS NOT NULL) AS profile_complete
            FROM candidate_portal_profiles p
            JOIN users u ON u.user_id = p.user_id
            WHERE p.user_id = ?
            """;

    private static final RowMapper<CandidateProfileResponse> PROFILE_ROW_MAPPER =
            (resultSet, rowNumber) -> new CandidateProfileResponse(
                    resultSet.getString("full_name"),
                    resultSet.getString("email"),
                    resultSet.getString("mobile"),
                    resultSet.getString("qualification"),
                    resultSet.getObject("passing_year", Integer.class),
                    resultSet.getBigDecimal("percentage"),
                    resultSet.getObject("backlog_count", Integer.class),
                    resultSet.getString("skills"),
                    resultSet.getString("experience"),
                    resultSet.getBoolean("profile_complete"),
                    resultSet.getString("resume_original_name") != null,
                    resultSet.getString("resume_original_name"));

    private final JdbcTemplate jdbcTemplate;
    private final JobApplicationDao applicationDao;
    private final InterviewDao interviewDao;
    private final OfferDao offerDao;

    public CandidatePortalDao(
            JdbcTemplate jdbcTemplate,
            JobApplicationDao applicationDao,
            InterviewDao interviewDao,
            OfferDao offerDao) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationDao = applicationDao;
        this.interviewDao = interviewDao;
        this.offerDao = offerDao;
    }

    public CandidateProfileResponse findProfile(long userId) {
        try {
            return jdbcTemplate.queryForObject(PROFILE_QUERY, PROFILE_ROW_MAPPER, userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw profileNotFound();
        }
    }

    public long findCandidateId(long userId) {
        Long candidateId = findCandidateIdOrNull(userId);
        if (candidateId == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Complete your education and contact details before applying for jobs");
        }
        return candidateId;
    }

    public Long findCandidateIdOrNull(long userId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT candidate_id
                    FROM candidate_portal_profiles
                    WHERE user_id = ?
                    """, Long.class, userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw profileNotFound();
        }
    }

    public void saveProfile(long userId, CandidateProfileRequest request) {
        int updatedProfile = jdbcTemplate.update("""
                UPDATE candidate_portal_profiles
                SET mobile = ?, qualification = ?, passing_year = ?, percentage = ?,
                    backlog_count = ?, skills = ?, experience = ?
                WHERE user_id = ?
                """,
                request.mobile().trim(),
                request.qualification().trim(),
                request.passingYear(),
                request.percentage(),
                request.backlogCount(),
                normalize(request.skills()),
                normalize(request.experience()),
                userId);
        if (updatedProfile != 1) {
            throw profileNotFound();
        }

        jdbcTemplate.update(
                "UPDATE users SET full_name = ? WHERE user_id = ?",
                request.fullName().trim(),
                userId);
        String email = jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE user_id = ?",
                String.class,
                userId);
        Long candidateId = jdbcTemplate.queryForObject(
                "SELECT candidate_id FROM candidate_portal_profiles WHERE user_id = ?",
                (resultSet, rowNumber) -> resultSet.getObject("candidate_id", Long.class),
                userId);
        if (candidateId == null) {
            candidateId = jdbcTemplate.query(
                    "SELECT candidate_id FROM candidates WHERE LOWER(email) = LOWER(?)",
                    resultSet -> resultSet.next() ? resultSet.getLong("candidate_id") : null,
                    email);
        }
        if (candidateId == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO candidates (
                            full_name, email, mobile, qualification, passing_year,
                            percentage, backlog_count, skills
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, request.fullName().trim());
                statement.setString(2, email);
                statement.setString(3, request.mobile().trim());
                statement.setString(4, request.qualification().trim());
                statement.setInt(5, request.passingYear());
                statement.setBigDecimal(6, request.percentage());
                statement.setInt(7, request.backlogCount());
                statement.setString(8, normalize(request.skills()));
                return statement;
            }, keyHolder);
            Number generatedId = keyHolder.getKey();
            if (generatedId == null) {
                throw new IllegalStateException("The database did not return the candidate profile ID");
            }
            candidateId = generatedId.longValue();
        } else {
            jdbcTemplate.update("""
                    UPDATE candidates
                    SET full_name = ?, mobile = ?, qualification = ?, passing_year = ?,
                        percentage = ?, backlog_count = ?, skills = ?
                    WHERE candidate_id = ?
                    """,
                    request.fullName().trim(),
                    request.mobile().trim(),
                    request.qualification().trim(),
                    request.passingYear(),
                    request.percentage(),
                    request.backlogCount(),
                    normalize(request.skills()),
                    candidateId);
        }
        jdbcTemplate.update(
                "UPDATE candidate_portal_profiles SET candidate_id = ? WHERE user_id = ?",
                candidateId,
                userId);
    }

    public CandidateResumeMetadata findResume(long userId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT resume_original_name, resume_storage_key
                    FROM candidate_portal_profiles
                    WHERE user_id = ? AND resume_storage_key IS NOT NULL
                    """,
                    (resultSet, rowNumber) -> new CandidateResumeMetadata(
                            resultSet.getString("resume_original_name"),
                            resultSet.getString("resume_storage_key")),
                    userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume was not found");
        }
    }

    public CandidateResumeMetadata findResumeForCandidate(long candidateId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT resume_original_name, resume_storage_key
                    FROM candidate_portal_profiles
                    WHERE candidate_id = ? AND resume_storage_key IS NOT NULL
                    """,
                    (resultSet, rowNumber) -> new CandidateResumeMetadata(
                            resultSet.getString("resume_original_name"),
                            resultSet.getString("resume_storage_key")),
                    candidateId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume was not found");
        }
    }

    public String updateResume(long userId, String originalName, String storageKey) {
        String oldStorageKey = jdbcTemplate.queryForObject("""
                SELECT resume_storage_key
                FROM candidate_portal_profiles
                WHERE user_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> resultSet.getString("resume_storage_key"),
                userId);
        int updated = jdbcTemplate.update("""
                UPDATE candidate_portal_profiles
                SET resume_original_name = ?, resume_storage_key = ?
                WHERE user_id = ?
                """,
                originalName,
                storageKey,
                userId);
        if (updated != 1) {
            throw profileNotFound();
        }
        return oldStorageKey;
    }

    public ApplicationPageResponse findApplications(long candidateId, int page, int size) {
        long total = count("""
                SELECT COUNT(*) FROM applications WHERE candidate_id = ?
                """, candidateId);
        List<Long> ids = findIds("""
                SELECT application_id FROM applications WHERE candidate_id = ?
                ORDER BY applied_at DESC, application_id DESC LIMIT ? OFFSET ?
                """, candidateId, page, size);
        return new ApplicationPageResponse(
                ids.stream().map(applicationDao::findById).toList(),
                page,
                size,
                total,
                pages(total, size));
    }

    public List<com.recruitment.dto.ApplicationStatusHistoryResponse> findApplicationHistory(
            long candidateId,
            long applicationId) {
        if (!exists("""
                SELECT COUNT(*) FROM applications
                WHERE application_id = ? AND candidate_id = ?
                """, applicationId, candidateId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application was not found");
        }
        return applicationDao.findHistory(applicationId);
    }

    public InterviewPageResponse findInterviews(long candidateId, int page, int size) {
        long total = count("""
                SELECT COUNT(*) FROM interviews i
                JOIN applications a ON a.application_id = i.application_id
                WHERE a.candidate_id = ?
                """, candidateId);
        List<Long> ids = findIds("""
                SELECT i.interview_id FROM interviews i
                JOIN applications a ON a.application_id = i.application_id
                WHERE a.candidate_id = ?
                ORDER BY i.scheduled_at DESC, i.interview_id DESC LIMIT ? OFFSET ?
                """, candidateId, page, size);
        return new InterviewPageResponse(
                ids.stream().map(interviewDao::findById).toList(),
                page,
                size,
                total,
                pages(total, size));
    }

    public OfferPageResponse findOffers(long candidateId, int page, int size) {
        long total = count("""
                SELECT COUNT(*) FROM offers o
                JOIN applications a ON a.application_id = o.application_id
                WHERE a.candidate_id = ?
                """, candidateId);
        List<Long> ids = findIds("""
                SELECT o.offer_id FROM offers o
                JOIN applications a ON a.application_id = o.application_id
                WHERE a.candidate_id = ?
                ORDER BY o.offer_date DESC, o.offer_id DESC LIMIT ? OFFSET ?
                """, candidateId, page, size);
        return new OfferPageResponse(
                ids.stream().map(offerDao::findById).toList(),
                page,
                size,
                total,
                pages(total, size));
    }

    public List<CandidateNotificationResponse> findNotifications(long candidateId, int limit) {
        return jdbcTemplate.query("""
                SELECT event_id, event_type, message, occurred_at, reference_id
                FROM (
                    SELECT h.history_id AS event_id,
                           'APPLICATION_STATUS' AS event_type,
                           CONCAT('Application for ', j.job_title, ' changed to ', h.new_status) AS message,
                           h.changed_at AS occurred_at,
                           h.application_id AS reference_id
                    FROM application_status_history h
                    JOIN applications a ON a.application_id = h.application_id
                    JOIN jobs j ON j.job_id = a.job_id
                    WHERE a.candidate_id = ?
                    UNION ALL
                    SELECT i.interview_id AS event_id,
                           'INTERVIEW' AS event_type,
                           CONCAT('Interview for ', j.job_title, ' is ', i.status,
                                  ' and scheduled for ', DATE_FORMAT(i.scheduled_at, '%Y-%m-%d %H:%i')) AS message,
                           i.updated_at AS occurred_at,
                           i.interview_id AS reference_id
                    FROM interviews i
                    JOIN applications a ON a.application_id = i.application_id
                    JOIN jobs j ON j.job_id = a.job_id
                    WHERE a.candidate_id = ?
                    UNION ALL
                    SELECT o.offer_id AS event_id,
                           'OFFER' AS event_type,
                           CONCAT('Offer for ', o.designation, ' is ', o.status) AS message,
                           o.updated_at AS occurred_at,
                           o.offer_id AS reference_id
                    FROM offers o
                    JOIN applications a ON a.application_id = o.application_id
                    WHERE a.candidate_id = ?
                ) portal_updates
                ORDER BY occurred_at DESC, event_id DESC
                LIMIT ?
                """,
                (resultSet, rowNumber) -> new CandidateNotificationResponse(
                        resultSet.getLong("event_id"),
                        resultSet.getString("event_type"),
                        resultSet.getString("message"),
                        resultSet.getTimestamp("occurred_at").toLocalDateTime(),
                        resultSet.getLong("reference_id")),
                candidateId,
                candidateId,
                candidateId,
                limit);
    }

    private long count(String sql, long candidateId) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, candidateId);
        return count == null ? 0 : count;
    }

    private List<Long> findIds(String sql, long candidateId, int page, int size) {
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> resultSet.getLong(1),
                candidateId,
                size,
                (long) page * size);
    }

    private boolean exists(String sql, long firstId, long candidateId) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, firstId, candidateId);
        return count != null && count > 0;
    }

    private static int pages(long total, int size) {
        return (int) Math.ceil((double) total / size);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ResponseStatusException profileNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate profile was not found");
    }
}
