package org.multivehicle.covehicle.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationRequest(
        @NotNull Long tripId,
        @NotNull Long pickupStopId,
        @NotNull Long dropoffStopId,
        @NotNull @Min(1) Integer numberOfSeats
) {
}
