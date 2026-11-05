package com.recruitment.dao;

import com.recruitment.dto.CompanyPageResponse;
import com.recruitment.dto.CompanyRequest;
import com.recruitment.dto.CompanyResponse;
import com.recruitment.dto.CompanyStatus;
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
public class CompanyDao {

    private static final String COMPANY_COLUMNS = """
            company_id, company_name, industry, location, contact_person, email,
            mobile, website, status, created_at, updated_at
            """;

    private static final RowMapper<CompanyResponse> COMPANY_ROW_MAPPER =
            (resultSet, rowNumber) -> new CompanyResponse(
                    resultSet.getLong("company_id"),
                    resultSet.getString("company_name"),
                    resultSet.getString("industry"),
                    resultSet.getString("location"),
                    resultSet.getString("contact_person"),
                    resultSet.getString("email"),
                    resultSet.getString("mobile"),
                    resultSet.getString("website"),
                    CompanyStatus.valueOf(resultSet.getString("status")),
                    timestampToLocalDateTime(resultSet.getTimestamp("created_at")),
                    timestampToLocalDateTime(resultSet.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    public CompanyDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CompanyResponse findById(long companyId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + COMPANY_COLUMNS + " FROM companies WHERE company_id = ?",
                COMPANY_ROW_MAPPER,
                companyId);
    }

    public CompanyPageResponse findAll(
            String search,
            String name,
            String industry,
            String location,
            CompanyStatus status,
            int page,
            int size) {
        StringBuilder where = new StringBuilder(" FROM companies WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();

        if (hasText(search)) {
            where.append("""
                     AND (LOWER(company_name) LIKE ? OR LOWER(COALESCE(industry, '')) LIKE ?
                     OR LOWER(COALESCE(location, '')) LIKE ?)
                    """);
            String pattern = containsPattern(search);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        addLikeFilter(where, parameters, "company_name", name);
        addLikeFilter(where, parameters, "industry", industry);
        addLikeFilter(where, parameters, "location", location);
        if (status != null) {
            where.append(" AND status = ?");
            parameters.add(status.name());
        }

        Long totalElements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + where,
                Long.class,
                parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<CompanyResponse> companies = jdbcTemplate.query(
                "SELECT " + COMPANY_COLUMNS + where
                        + " ORDER BY company_id LIMIT ? OFFSET ?",
                COMPANY_ROW_MAPPER,
                pageParameters.toArray());
        long total = totalElements == null ? 0 : totalElements;
        int totalPages = (int) Math.ceil((double) total / size);
        return new CompanyPageResponse(companies, page, size, total, totalPages);
    }

    public long insert(CompanyRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO companies (
                        company_name, industry, location, contact_person, email,
                        mobile, website, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            setCompanyParameters(statement, request);
            statement.setString(8, request.status() == null
                    ? CompanyStatus.ACTIVE.name()
                    : request.status().name());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("The database did not return the created company ID");
        }
        return key.longValue();
    }

    public void update(long companyId, CompanyRequest request) {
        jdbcTemplate.update("""
                UPDATE companies
                SET company_name = ?, industry = ?, location = ?, contact_person = ?,
                    email = ?, mobile = ?, website = ?, status = COALESCE(?, status)
                WHERE company_id = ?
                """,
                normalize(request.companyName()),
                normalize(request.industry()),
                normalize(request.location()),
                normalize(request.contactPerson()),
                normalizeEmail(request.email()),
                normalize(request.mobile()),
                normalize(request.website()),
                request.status() == null ? null : request.status().name(),
                companyId);
    }

    public boolean delete(long companyId) {
        return jdbcTemplate.update(
                "DELETE FROM companies WHERE company_id = ?",
                companyId) > 0;
    }

    public boolean hasJobs(long companyId) {
        Integer jobCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jobs WHERE company_id = ?",
                Integer.class,
                companyId);
        return jobCount != null && jobCount > 0;
    }

    private static void setCompanyParameters(
            java.sql.PreparedStatement statement,
            CompanyRequest request) throws java.sql.SQLException {
        statement.setString(1, normalize(request.companyName()));
        statement.setString(2, normalize(request.industry()));
        statement.setString(3, normalize(request.location()));
        statement.setString(4, normalize(request.contactPerson()));
        statement.setString(5, normalizeEmail(request.email()));
        statement.setString(6, normalize(request.mobile()));
        statement.setString(7, normalize(request.website()));
    }

    private static void addLikeFilter(
            StringBuilder where,
            List<Object> parameters,
            String column,
            String value) {
        if (hasText(value)) {
            where.append(" AND LOWER(COALESCE(").append(column).append(", '')) LIKE ?");
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

    private static String normalizeEmail(String email) {
        return hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

    private static java.time.LocalDateTime timestampToLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
