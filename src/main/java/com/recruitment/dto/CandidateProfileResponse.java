package com.recruitment.dto;

import java.math.BigDecimal;

public record CandidateProfileResponse(
        String fullName,
        String email,
        String mobile,
        String qualification,
        Integer passingYear,
        BigDecimal percentage,
        Integer backlogCount,
        String skills,
        String experience,
        boolean profileComplete,
        boolean resumeAvailable,
        String resumeName) {
}
