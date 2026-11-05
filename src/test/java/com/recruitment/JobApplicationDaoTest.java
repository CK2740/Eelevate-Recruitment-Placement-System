package com.recruitment;

import static org.mockito.Mockito.verify;

import com.recruitment.dao.JobApplicationDao;
import com.recruitment.dto.ApplicationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JobApplicationDaoTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private JobApplicationDao applicationDao;

    @Test
    void statusUpdatePersistsCurrentStateAndAppendsStatusHistory() {
        applicationDao.updateStatus(
                12,
                ApplicationStatus.APPLIED,
                ApplicationStatus.SHORTLISTED,
                7,
                "Strong match");

        verify(jdbcTemplate).update(
                "UPDATE applications SET status = ? WHERE application_id = ?",
                ApplicationStatus.SHORTLISTED.name(),
                12L);
        verify(jdbcTemplate).update(
                """
                INSERT INTO application_status_history (
                    application_id, previous_status, new_status, changed_by_user_id, change_reason
                ) VALUES (?, ?, ?, ?, ?)
                """,
                12L,
                ApplicationStatus.APPLIED.name(),
                ApplicationStatus.SHORTLISTED.name(),
                7L,
                "Strong match");
    }
}
