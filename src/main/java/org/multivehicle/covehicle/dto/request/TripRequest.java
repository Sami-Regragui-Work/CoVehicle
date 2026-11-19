package org.multivehicle.covehicle.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TripRequest(
        @NotNull Long vehicleId,
        @NotNull @Min(1) Integer seatsOffered,
        @NotEmpty List<@NotNull @Valid StopRequest> stops
) {
}
