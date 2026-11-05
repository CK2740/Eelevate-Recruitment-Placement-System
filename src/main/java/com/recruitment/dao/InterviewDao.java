package com.recruitment.dao;

import com.recruitment.dto.InterviewMode;
import com.recruitment.dto.InterviewPageResponse;
import com.recruitment.dto.InterviewRecommendation;
import com.recruitment.dto.InterviewRequest;
import com.recruitment.dto.InterviewResponse;
import com.recruitment.dto.InterviewStatus;
import com.recruitment.dto.InterviewStatusHistoryResponse;
import com.recruitment.dto.InterviewerFeedbackRequest;
import com.recruitment.dto.InterviewerFeedbackResponse;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class InterviewDao {

    private static final String INTERVIEW_COLUMNS = """
            i.interview_id, i.application_id, a.candidate_id, c.full_name AS candidate_name,
            j.job_id, j.job_title, co.company_name, i.interview_round, i.interview_type,
            i.round_number, i.scheduled_at, i.duration_minutes, i.interview_mode,
            i.location, i.meeting_link, i.status, i.created_at, i.updated_at
            """;

    private static final String INTERVIEW_FROM = """
             FROM interviews i
             JOIN applications a ON a.application_id = i.application_id
             JOIN candidates c ON c.candidate_id = a.candidate_id
             JOIN jobs j ON j.job_id = a.job_id
             JOIN companies co ON co.company_id = j.company_id
            """;

    private static final RowMapper<InterviewResponse> INTERVIEW_ROW_MAPPER =
            (resultSet, rowNumber) -> new InterviewResponse(
                    resultSet.getLong("interview_id"),
                    resultSet.getLong("application_id"),
                    resultSet.getLong("candidate_id"),
                    resultSet.getString("candidate_name"),
                    resultSet.getLong("job_id"),
                    resultSet.getString("job_title"),
                    resultSet.getString("company_name"),
                    resultSet.getString("interview_round"),
                    resultSet.getString("interview_type"),
                    resultSet.getInt("round_number"),
                    resultSet.getTimestamp("scheduled_at").toLocalDateTime(),
                    resultSet.getInt("duration_minutes"),
                    InterviewMode.valueOf(resultSet.getString("interview_mode")),
                    resultSet.getString("location"),
                    resultSet.getString("meeting_link"),
                    InterviewStatus.valueOf(resultSet.getString("status")),
                    List.of(),
                    timestampToLocalDateTime(resultSet.getTimestamp("created_at")),
                    timestampToLocalDateTime(resultSet.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    public InterviewDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public InterviewContext lockApplication(long applicationId) {
        InterviewContext context = jdbcTemplate.queryForObject("""
                SELECT a.application_id, a.candidate_id, j.created_by_user_id
                FROM applications a
                JOIN jobs j ON j.job_id = a.job_id
                WHERE a.application_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new InterviewContext(
                        resultSet.getLong("application_id"),
                        resultSet.getLong("candidate_id"),
                        resultSet.getObject("created_by_user_id", Long.class)),
                applicationId);
        jdbcTemplate.queryForObject(
                "SELECT candidate_id FROM candidates WHERE candidate_id = ? FOR UPDATE",
                Long.class,
                context.candidateId());
        return context;
    }

    public InterviewContext lockInterview(long interviewId) {
        InterviewContext context = jdbcTemplate.queryForObject("""
                SELECT i.interview_id, a.candidate_id, j.created_by_user_id,
                       i.application_id, i.status
                FROM interviews i
                JOIN applications a ON a.application_id = i.application_id
                JOIN jobs j ON j.job_id = a.job_id
                WHERE i.interview_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new InterviewContext(
                        resultSet.getLong("application_id"),
                        resultSet.getLong("candidate_id"),
                        resultSet.getObject("created_by_user_id", Long.class),
                        InterviewStatus.valueOf(resultSet.getString("status")),
                        resultSet.getLong("interview_id")),
                interviewId);
        jdbcTemplate.queryForObject(
                "SELECT candidate_id FROM candidates WHERE candidate_id = ? FOR UPDATE",
                Long.class,
                context.candidateId());
        return context;
    }

    public boolean hasCandidateConflict(
            long candidateId,
            LocalDateTime start,
            int durationMinutes,
            Long excludedInterviewId) {
        String excludedInterviewFilter = excludedInterviewId == null
                ? ""
                : " AND i.interview_id <> ?";
        List<Object> parameters = new ArrayList<>(List.of(
                candidateId,
                Timestamp.valueOf(start),
                durationMinutes,
                Timestamp.valueOf(start)));
        if (excludedInterviewId != null) {
            parameters.add(excludedInterviewId);
        }
        Integer conflictCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM interviews i
                JOIN applications a ON a.application_id = i.application_id
                WHERE a.candidate_id = ?
                  AND i.status = 'SCHEDULED'
                  AND i.scheduled_at < DATE_ADD(?, INTERVAL ? MINUTE)
                  AND DATE_ADD(i.scheduled_at, INTERVAL i.duration_minutes MINUTE) > ?
                """ + excludedInterviewFilter,
                Integer.class,
                parameters.toArray());
        return conflictCount != null && conflictCount > 0;
    }

    public long insert(InterviewRequest request, long createdByUserId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO interviews (
                        application_id, interview_round, interview_type, round_number,
                        scheduled_at, duration_minutes, interview_mode, location,
                        meeting_link, interviewer, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SCHEDULED')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, request.applicationId());
            statement.setString(2, request.interviewRound().trim());
            statement.setString(3, request.interviewType());
            statement.setInt(4, request.roundNumber());
            statement.setTimestamp(5, Timestamp.valueOf(request.scheduledAt()));
            statement.setInt(6, request.durationMinutes());
            statement.setString(7, request.interviewMode().name());
            statement.setString(8, normalize(request.location()));
            statement.setString(9, normalize(request.meetingLink()));
            statement.setString(10, interviewerSummary(request));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The database did not return the created interview ID");
        }
        long interviewId = key.longValue();
        insertInterviewers(interviewId, request);
        insertStatusHistory(interviewId, null, InterviewStatus.SCHEDULED, createdByUserId, "Interview scheduled");
        return interviewId;
    }

    public boolean updateSchedule(
            long interviewId,
            LocalDateTime scheduledAt,
            Integer durationMinutes,
            String location,
            String meetingLink,
            long changedByUserId) {
        int updated = jdbcTemplate.update("""
                UPDATE interviews
                SET scheduled_at = ?, duration_minutes = COALESCE(?, duration_minutes),
                    location = COALESCE(?, location), meeting_link = COALESCE(?, meeting_link)
                WHERE interview_id = ? AND status = 'SCHEDULED'
                """,
                Timestamp.valueOf(scheduledAt),
                durationMinutes,
                normalize(location),
                normalize(meetingLink),
                interviewId);
        if (updated == 0 && !exists(interviewId)) {
            return false;
        }
        insertStatusHistory(
                interviewId,
                InterviewStatus.SCHEDULED,
                InterviewStatus.SCHEDULED,
                changedByUserId,
                "Interview rescheduled");
        return true;
    }

    public boolean updateStatus(
            long interviewId,
            InterviewStatus previousStatus,
            InterviewStatus newStatus,
            long changedByUserId,
            String reason) {
        int updated = jdbcTemplate.update(
                "UPDATE interviews SET status = ? WHERE interview_id = ? AND status = ?",
                newStatus.name(),
                interviewId,
                previousStatus.name());
        if (updated == 0) {
            return false;
        }
        insertStatusHistory(interviewId, previousStatus, newStatus, changedByUserId, reason);
        return true;
    }

    public boolean saveFeedback(
            long interviewId,
            long interviewerId,
            InterviewerFeedbackRequest feedback) {
        Integer interviewerCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM interview_interviewers
                WHERE interview_id = ? AND interviewer_id = ?
                """,
                Integer.class,
                interviewId,
                interviewerId);
        if (interviewerCount == null || interviewerCount == 0) {
            return false;
        }
        jdbcTemplate.update("""
                INSERT INTO interview_interviewer_feedback (
                    interviewer_id, technical_rating, communication_rating, recommendation, comments
                ) VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    technical_rating = VALUES(technical_rating),
                    communication_rating = VALUES(communication_rating),
                    recommendation = VALUES(recommendation),
                    comments = VALUES(comments)
                """,
                interviewerId,
                feedback.technicalRating(),
                feedback.communicationRating(),
                feedback.recommendation().name(),
                normalize(feedback.comments()));
        return true;
    }

    public InterviewResponse findById(long interviewId) {
        InterviewResponse interview = jdbcTemplate.queryForObject(
                "SELECT " + INTERVIEW_COLUMNS + INTERVIEW_FROM + " WHERE i.interview_id = ?",
                INTERVIEW_ROW_MAPPER,
                interviewId);
        return withInterviewers(interview);
    }

    public InterviewPageResponse findAll(
            String search,
            String candidate,
            String job,
            String company,
            InterviewStatus status,
            InterviewMode mode,
            LocalDateTime from,
            LocalDateTime to,
            Long recruiterId,
            int page,
            int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (hasText(search)) {
            String pattern = containsPattern(search);
            where.append("""
                     AND (LOWER(c.full_name) LIKE ? OR LOWER(j.job_title) LIKE ?
                     OR LOWER(co.company_name) LIKE ?)
                    """);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        addLikeFilter(where, parameters, "c.full_name", candidate);
        addLikeFilter(where, parameters, "j.job_title", job);
        addLikeFilter(where, parameters, "co.company_name", company);
        if (status != null) {
            where.append(" AND i.status = ?");
            parameters.add(status.name());
        }
        if (mode != null) {
            where.append(" AND i.interview_mode = ?");
            parameters.add(mode.name());
        }
        if (from != null) {
            where.append(" AND i.scheduled_at >= ?");
            parameters.add(Timestamp.valueOf(from));
        }
        if (to != null) {
            where.append(" AND i.scheduled_at <= ?");
            parameters.add(Timestamp.valueOf(to));
        }
        if (recruiterId != null) {
            where.append(" AND j.created_by_user_id = ?");
            parameters.add(recruiterId);
        }

        Long totalElements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + INTERVIEW_FROM + where,
                Long.class,
                parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<Long> interviewIds = jdbcTemplate.query(
                "SELECT i.interview_id" + INTERVIEW_FROM + where
                        + " ORDER BY i.scheduled_at, i.interview_id LIMIT ? OFFSET ?",
                (resultSet, rowNumber) -> resultSet.getLong(1),
                pageParameters.toArray());
        List<InterviewResponse> interviews = interviewIds.stream()
                .map(this::findById)
                .toList();
        long total = totalElements == null ? 0 : totalElements;
        int totalPages = (int) Math.ceil((double) total / size);
        return new InterviewPageResponse(interviews, page, size, total, totalPages);
    }

    public List<InterviewStatusHistoryResponse> findHistory(long interviewId) {
        return jdbcTemplate.query("""
                SELECT history_id, previous_status, new_status, changed_by_user_id,
                       change_reason, changed_at
                FROM interview_status_history
                WHERE interview_id = ?
                ORDER BY changed_at, history_id
                """,
                (resultSet, rowNumber) -> new InterviewStatusHistoryResponse(
                        resultSet.getLong("history_id"),
                        resultSet.getString("previous_status") == null
                                ? null
                                : InterviewStatus.valueOf(resultSet.getString("previous_status")),
                        InterviewStatus.valueOf(resultSet.getString("new_status")),
                        resultSet.getObject("changed_by_user_id", Long.class),
                        resultSet.getString("change_reason"),
                        timestampToLocalDateTime(resultSet.getTimestamp("changed_at"))),
                interviewId);
    }

    public boolean recruiterOwnsInterview(long interviewId, long recruiterId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM interviews i
                JOIN applications a ON a.application_id = i.application_id
                JOIN jobs j ON j.job_id = a.job_id
                WHERE i.interview_id = ? AND j.created_by_user_id = ?
                """,
                Integer.class,
                interviewId,
                recruiterId);
        return count != null && count > 0;
    }

    public boolean exists(long interviewId) {
        return existsInterview(interviewId);
    }

    private boolean existsInterview(long interviewId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interviews WHERE interview_id = ?",
                Integer.class,
                interviewId);
        return count != null && count > 0;
    }

    private void insertInterviewers(long interviewId, InterviewRequest request) {
        for (var interviewer : request.interviewers()) {
            jdbcTemplate.update("""
                    INSERT INTO interview_interviewers (interview_id, interviewer_name, interviewer_email)
                    VALUES (?, ?, ?)
                    """,
                    interviewId,
                    interviewer.name().trim(),
                    normalize(interviewer.email()));
        }
    }

    private void insertStatusHistory(
            long interviewId,
            InterviewStatus previousStatus,
            InterviewStatus newStatus,
            long changedByUserId,
            String reason) {
        jdbcTemplate.update("""
                INSERT INTO interview_status_history (
                    interview_id, previous_status, new_status, changed_by_user_id, change_reason
                ) VALUES (?, ?, ?, ?, ?)
                """,
                interviewId,
                previousStatus == null ? null : previousStatus.name(),
                newStatus.name(),
                changedByUserId,
                normalize(reason));
    }

    private InterviewResponse withInterviewers(InterviewResponse interview) {
        List<InterviewerFeedbackResponse> interviewers = jdbcTemplate.query("""
                SELECT ii.interviewer_id, ii.interviewer_name, ii.interviewer_email,
                       f.technical_rating, f.communication_rating, f.recommendation,
                       f.comments, f.updated_at
                FROM interview_interviewers ii
                LEFT JOIN interview_interviewer_feedback f ON f.interviewer_id = ii.interviewer_id
                WHERE ii.interview_id = ?
                ORDER BY ii.interviewer_id
                """,
                (resultSet, rowNumber) -> new InterviewerFeedbackResponse(
                        resultSet.getLong("interviewer_id"),
                        resultSet.getString("interviewer_name"),
                        resultSet.getString("interviewer_email"),
                        resultSet.getBigDecimal("technical_rating"),
                        resultSet.getBigDecimal("communication_rating"),
                        resultSet.getString("recommendation") == null
                                ? null
                                : InterviewRecommendation.valueOf(resultSet.getString("recommendation")),
                        resultSet.getString("comments"),
                        timestampToLocalDateTime(resultSet.getTimestamp("updated_at"))),
                interview.interviewId());
        return new InterviewResponse(
                interview.interviewId(),
                interview.applicationId(),
                interview.candidateId(),
                interview.candidateName(),
                interview.jobId(),
                interview.jobTitle(),
                interview.companyName(),
                interview.interviewRound(),
                interview.interviewType(),
                interview.roundNumber(),
                interview.scheduledAt(),
                interview.durationMinutes(),
                interview.interviewMode(),
                interview.location(),
                interview.meetingLink(),
                interview.status(),
                interviewers,
                interview.createdAt(),
                interview.updatedAt());
    }

    private static void addLikeFilter(
            StringBuilder where,
            List<Object> parameters,
            String column,
            String value) {
        if (hasText(value)) {
            where.append(" AND LOWER(").append(column).append(") LIKE ?");
            parameters.add(containsPattern(value));
        }
    }

    private static String interviewerSummary(InterviewRequest request) {
        return request.interviewers().stream()
                .map(interviewer -> interviewer.name().trim())
                .findFirst()
                .orElse(null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String containsPattern(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static LocalDateTime timestampToLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record InterviewContext(
            long applicationId,
            long candidateId,
            Long assignedRecruiterId,
            InterviewStatus status,
            long interviewId) {
        public InterviewContext(long applicationId, long candidateId, Long assignedRecruiterId) {
            this(applicationId, candidateId, assignedRecruiterId, InterviewStatus.SCHEDULED, 0);
        }
    }
}
