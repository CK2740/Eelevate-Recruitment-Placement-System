package com.recruitment.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicRegistrationRequest(
        @jakarta.validation.constraints.NotNull PublicAccountType accountType,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(min = 12, max = 72) String confirmPassword) {

    @AssertTrue(message = "Password confirmation must match password")
    public boolean isPasswordConfirmationValid() {
        return password != null && password.equals(confirmPassword);
    }
}
