package com.recruitment.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InterviewResponse(
        long interviewId,
        long applicationId,
        long candidateId,
        String candidateName,
        long jobId,
        String jobTitle,
        String companyName,
        String interviewRound,
        String interviewType,
        int roundNumber,
        LocalDateTime scheduledAt,
        int durationMinutes,
        InterviewMode interviewMode,
        String location,
        String meetingLink,
        InterviewStatus status,
        List<InterviewerFeedbackResponse> interviewers,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
