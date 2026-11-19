package org.multivehicle.covehicle.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleRequest(
        @NotBlank String brand,
        @NotBlank String model,
        String color,
        @NotBlank String licensePlate,
        @NotNull @Min(1) @Max(8) Integer capacity
) {
}
