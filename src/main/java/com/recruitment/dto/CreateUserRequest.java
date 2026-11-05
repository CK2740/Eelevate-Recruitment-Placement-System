package com.recruitment.dto;

import com.recruitment.model.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotNull RoleCode role) {
}
