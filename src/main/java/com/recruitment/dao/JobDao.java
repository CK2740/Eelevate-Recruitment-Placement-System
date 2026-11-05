package com.recruitment.dao;

import com.recruitment.dto.EmploymentType;
import com.recruitment.dto.JobEligibilityRequest;
import com.recruitment.dto.JobPageResponse;
import com.recruitment.dto.JobRequest;
import com.recruitment.dto.JobResponse;
import com.recruitment.dto.JobStatus;
import java.sql.Date;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JobDao {

    private static final String JOB_COLUMNS = """
            j.job_id, j.company_id, c.company_name, j.job_title, j.technology, j.openings,
            j.location, j.package_amount, j.job_description, j.skills, j.application_deadline,
            j.employment_type, j.status, ec.minimum_percentage, ec.minimum_passing_year,
            ec.maximum_passing_year, ec.maximum_backlogs, j.created_at, j.updated_at
            """;

    private static final String JOB_FROM = """
             FROM jobs j
             JOIN companies c ON c.company_id = j.company_id
             LEFT JOIN job_eligibility_criteria ec ON ec.job_id = j.job_id
            """;

    private static final RowMapper<JobResponse> JOB_ROW_MAPPER =
            (resultSet, rowNumber) -> {
                Integer minimumPassingYear = resultSet.getObject("minimum_passing_year", Integer.class);
                Integer maximumPassingYear = resultSet.getObject("maximum_passing_year", Integer.class);
                Integer maximumBacklogs = resultSet.getObject("maximum_backlogs", Integer.class);
                var minimumPercentage = resultSet.getBigDecimal("minimum_percentage");
                JobEligibilityRequest eligibility = new JobEligibilityRequest(
                        minimumPercentage,
                        minimumPassingYear,
                        maximumPassingYear,
                        maximumBacklogs);
                Date deadline = resultSet.getDate("application_deadline");
                return new JobResponse(
                        resultSet.getLong("job_id"),
                        resultSet.getLong("company_id"),
                        resultSet.getString("company_name"),
                        resultSet.getString("job_title"),
                        resultSet.getString("technology"),
                        resultSet.getInt("openings"),
                        resultSet.getString("location"),
                        resultSet.getBigDecimal("package_amount"),
                        resultSet.getString("job_description"),
                        resultSet.getString("skills"),
                        deadline.toLocalDate(),
                        EmploymentType.valueOf(resultSet.getString("employment_type")),
                        JobStatus.valueOf(resultSet.getString("status")),
                        eligibility,
                        timestampToLocalDateTime(resultSet.getTimestamp("created_at")),
                        timestampToLocalDateTime(resultSet.getTimestamp("updated_at")));
            };

    private final JdbcTemplate jdbcTemplate;

    public JobDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public JobResponse findById(long jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + JOB_COLUMNS + JOB_FROM + " WHERE j.job_id = ?",
                JOB_ROW_MAPPER,
                jobId);
    }

    public JobPageResponse findAll(
            String search,
            String jobTitle,
            String company,
            String technology,
            String location,
            EmploymentType jobType,
            JobStatus status,
            Integer passingYear,
            int page,
            int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();

        if (hasText(search)) {
            where.append("""
                     AND (LOWER(j.job_title) LIKE ? OR LOWER(c.company_name) LIKE ?
                     OR LOWER(j.technology) LIKE ?)
                    """);
            String pattern = containsPattern(search);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        addLikeFilter(where, parameters, "j.job_title", jobTitle);
        addLikeFilter(where, parameters, "c.company_name", company);
        addLikeFilter(where, parameters, "j.technology", technology);
        addLikeFilter(where, parameters, "j.location", location);
        if (jobType != null) {
            where.append(" AND j.employment_type = ?");
            parameters.add(jobType.name());
        }
        if (status != null) {
            where.append(" AND j.status = ?");
            parameters.add(status.name());
        }
        if (passingYear != null) {
            where.append("""
                      AND (
                          NOT EXISTS (
                              SELECT 1 FROM job_eligibility_criteria filter_ec
                              WHERE filter_ec.job_id = j.job_id
                          )
                          OR EXISTS (
                              SELECT 1 FROM job_eligibility_criteria filter_ec
                              WHERE filter_ec.job_id = j.job_id
                                AND (filter_ec.minimum_passing_year IS NULL
                                     OR filter_ec.minimum_passing_year <= ?)
                                AND (filter_ec.maximum_passing_year IS NULL
                                     OR filter_ec.maximum_passing_year >= ?)
                          )
                      )
                    """);
            parameters.add(passingYear);
            parameters.add(passingYear);
        }

        Long totalElements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + JOB_FROM + where,
                Long.class,
                parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<JobResponse> jobs = jdbcTemplate.query(
                "SELECT " + JOB_COLUMNS + JOB_FROM + where
                        + " ORDER BY j.job_id LIMIT ? OFFSET ?",
                JOB_ROW_MAPPER,
                pageParameters.toArray());
        long total = totalElements == null ? 0 : totalElements;
        int totalPages = (int) Math.ceil((double) total / size);
        return new JobPageResponse(jobs, page, size, total, totalPages);
    }

    public boolean companyExists(long companyId) {
        Integer companyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM companies WHERE company_id = ?",
                Integer.class,
                companyId);
        return companyCount != null && companyCount > 0;
    }

    public long insert(JobRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO jobs (
                        company_id, job_title, technology, openings, location, package_amount,
                        job_description, skills, application_deadline, employment_type, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            setJobParameters(statement, request);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The database did not return the created job ID");
        }
        saveEligibility(key.longValue(), request.eligibilityCriteria());
        return key.longValue();
    }

    public boolean update(long jobId, JobRequest request) {
        int updated = jdbcTemplate.update("""
                UPDATE jobs
                SET company_id = ?, job_title = ?, technology = ?, openings = ?, location = ?,
                    package_amount = ?, job_description = ?, skills = ?, application_deadline = ?,
                    employment_type = ?, status = COALESCE(?, status)
                WHERE job_id = ?
                """,
                request.companyId(),
                normalize(request.jobTitle()),
                normalize(request.technology()),
                request.openings(),
                normalize(request.location()),
                request.packageAmount(),
                normalize(request.jobDescription()),
                normalize(request.skills()),
                Date.valueOf(request.applicationDeadline()),
                request.employmentType().name(),
                request.status() == null ? null : request.status().name(),
                jobId);
        if (updated > 0 || jobExists(jobId)) {
            saveEligibility(jobId, request.eligibilityCriteria());
            return true;
        }
        return false;
    }

    public boolean delete(long jobId) {
        return jdbcTemplate.update("DELETE FROM jobs WHERE job_id = ?", jobId) > 0;
    }

    public boolean hasApplications(long jobId) {
        Integer applicationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE job_id = ?",
                Integer.class,
                jobId);
        return applicationCount != null && applicationCount > 0;
    }

    private boolean jobExists(long jobId) {
        Integer jobCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jobs WHERE job_id = ?",
                Integer.class,
                jobId);
        return jobCount != null && jobCount > 0;
    }

    private void saveEligibility(long jobId, JobEligibilityRequest eligibility) {
        jdbcTemplate.update("""
                INSERT INTO job_eligibility_criteria (
                    job_id, minimum_percentage, minimum_passing_year,
                    maximum_passing_year, maximum_backlogs
                ) VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    minimum_percentage = VALUES(minimum_percentage),
                    minimum_passing_year = VALUES(minimum_passing_year),
                    maximum_passing_year = VALUES(maximum_passing_year),
                    maximum_backlogs = VALUES(maximum_backlogs)
                """,
                jobId,
                eligibility.minimumPercentage() == null
                        ? java.math.BigDecimal.ZERO
                        : eligibility.minimumPercentage(),
                eligibility.minimumPassingYear(),
                eligibility.maximumPassingYear(),
                eligibility.maximumBacklogs() == null ? 0 : eligibility.maximumBacklogs());
    }

    private static void setJobParameters(
            java.sql.PreparedStatement statement,
            JobRequest request) throws java.sql.SQLException {
        statement.setLong(1, request.companyId());
        statement.setString(2, normalize(request.jobTitle()));
        statement.setString(3, normalize(request.technology()));
        statement.setInt(4, request.openings());
        statement.setString(5, normalize(request.location()));
        statement.setBigDecimal(6, request.packageAmount());
        statement.setString(7, normalize(request.jobDescription()));
        statement.setString(8, normalize(request.skills()));
        statement.setDate(9, Date.valueOf(request.applicationDeadline()));
        statement.setString(10, request.employmentType().name());
        statement.setString(11, request.status() == null
                ? JobStatus.DRAFT.name()
                : request.status().name());
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String containsPattern(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static java.time.LocalDateTime timestampToLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
