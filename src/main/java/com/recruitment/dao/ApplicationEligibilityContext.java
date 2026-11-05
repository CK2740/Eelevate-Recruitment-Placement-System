package com.recruitment.dao;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ApplicationEligibilityContext(
        long candidateId,
        boolean candidateActive,
        String qualification,
        int passingYear,
        BigDecimal percentage,
        int backlogCount,
        String candidateSkills,
        long jobId,
        long companyId,
        Long assignedRecruiterId,
        String jobStatus,
        LocalDate applicationDeadline,
        String requiredQualification,
        String requiredSkills,
        BigDecimal minimumPercentage,
        Integer minimumPassingYear,
        Integer maximumPassingYear,
        Integer maximumBacklogs) {
}
