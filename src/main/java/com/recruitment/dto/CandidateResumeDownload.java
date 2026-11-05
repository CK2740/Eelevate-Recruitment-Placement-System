package com.recruitment.dto;

import org.springframework.core.io.Resource;

public record CandidateResumeDownload(Resource resource, String originalName, String contentType) {
}
