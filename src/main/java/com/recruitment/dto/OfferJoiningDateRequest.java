package com.recruitment.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record OfferJoiningDateRequest(
        @NotNull @FutureOrPresent LocalDate joiningDate) {
}
