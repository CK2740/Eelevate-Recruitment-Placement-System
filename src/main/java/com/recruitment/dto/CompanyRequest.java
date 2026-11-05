package com.recruitment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank @Size(max = 200) String companyName,
        @Size(max = 120) String industry,
        @Size(max = 200) String location,
        @Size(max = 160) String contactPerson,
        @Email @Size(max = 254) String email,
        @Pattern(regexp = "^(?:\\+?[0-9][0-9 ()-]{5,28}[0-9])?$",
                message = "must be a valid phone number")
        @Size(max = 30) String mobile,
        @Pattern(regexp = "^(?:https?://[^\\s/.]+(?:\\.[^\\s/]+)+(?:/[^\\s]*)?)?$",
                message = "must be a valid HTTP or HTTPS website URL")
        @Size(max = 255) String website,
        CompanyStatus status) {
}
