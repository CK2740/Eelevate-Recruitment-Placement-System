package com.recruitment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InterviewerRequest(
        @NotBlank @Size(max = 160) String name,
        @Email @Size(max = 254) String email) {
}
