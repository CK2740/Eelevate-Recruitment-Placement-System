package com.recruitment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InterviewerFeedbackResponse(
        long interviewerId,
        String interviewerName,
        String interviewerEmail,
        BigDecimal technicalRating,
        BigDecimal communicationRating,
        InterviewRecommendation recommendation,
        String comments,
        LocalDateTime updatedAt) {
}
