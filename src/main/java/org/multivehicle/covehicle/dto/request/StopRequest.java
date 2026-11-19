package org.multivehicle.covehicle.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StopRequest(
        @NotBlank String city,
        @NotNull LocalDateTime arrivalTime,
        @NotNull @Digits(integer = 8, fraction = 2) BigDecimal priceFromStart
) {
}
