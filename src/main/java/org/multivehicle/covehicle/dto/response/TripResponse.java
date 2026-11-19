package org.multivehicle.covehicle.dto.response;

import org.multivehicle.covehicle.entity.enums.TripStatus;

import java.time.LocalDateTime;
import java.util.List;

public record TripResponse(
        Long id,
        Long driverId,
        String driverName,
        VehicleSummary vehicle,
        String departureCity,
        String arrivalCity,
        LocalDateTime departureDate,
        int seatsOffered,
        int remainingSeats,
        TripStatus status,
        List<StopResponse> stops
) {
}
