package com.recruitment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record OfferRequest(
        @NotBlank @Size(max = 180) String designation,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
        BigDecimal packageAmount,
        @NotNull @FutureOrPresent LocalDate offerDate,
        @NotNull @FutureOrPresent LocalDate expiryDate,
        @Size(max = 10000) String offerDetails) {
}
