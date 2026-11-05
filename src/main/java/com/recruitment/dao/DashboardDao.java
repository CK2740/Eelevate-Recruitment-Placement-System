package com.recruitment.dao;

import com.recruitment.dto.DashboardMetricsResponse;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardDao {

    private final JdbcTemplate jdbcTemplate;

    public DashboardDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardMetricsResponse getMetrics() {
        return jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM candidates) AS total_candidates,
                    (SELECT COUNT(*) FROM companies) AS total_companies,
                    (SELECT COUNT(*) FROM jobs WHERE status = 'OPEN') AS open_jobs,
                    (SELECT COUNT(*) FROM applications) AS total_applications,
                    (SELECT COUNT(*) FROM interviews WHERE status = 'SCHEDULED') AS scheduled_interviews,
                    (SELECT COUNT(DISTINCT candidate_id) FROM applications WHERE status = 'SELECTED')
                        AS selected_candidates,
                    (SELECT COUNT(DISTINCT candidate_id) FROM applications WHERE status = 'JOINED')
                        AS joined_candidates
                """, (rs, rowNum) -> {
            long totalCandidates = rs.getLong("total_candidates");
            long joinedCandidates = rs.getLong("joined_candidates");
            BigDecimal placementPercentage = totalCandidates == 0
                    ? BigDecimal.ZERO.setScale(2)
                    : BigDecimal.valueOf(joinedCandidates)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(totalCandidates), 2, java.math.RoundingMode.HALF_UP);
            return new DashboardMetricsResponse(
                    totalCandidates,
                    rs.getLong("total_companies"),
                    rs.getLong("open_jobs"),
                    rs.getLong("total_applications"),
                    rs.getLong("scheduled_interviews"),
                    rs.getLong("selected_candidates"),
                    joinedCandidates,
                    placementPercentage);
        });
    }
}
