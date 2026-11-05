package com.recruitment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record OfferStatusRequest(
        @NotNull OfferStatus status,
        @Size(max = 500) String reason,
        @FutureOrPresent LocalDate joiningDate) {
}
