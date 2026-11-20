package org.multivehicle.covehicle.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TripSearchResult(
        Long tripId,
        String driverName,
        VehicleSummary vehicle,
        String pickupCity,
        LocalDateTime pickupTime,
        String dropoffCity,
        LocalDateTime dropoffTime,
        BigDecimal segmentPrice,
        Long remainingSeats,
        LocalDateTime departureDate
) {
    public TripSearchResult(
            Long tripId,
            String driverName,
            Long vehicleId,
            String brand,
            String model,
            String color,
            String licensePlate,
            Integer capacity,
            String pickupCity,
            LocalDateTime pickupTime,
            String dropoffCity,
            LocalDateTime dropoffTime,
            BigDecimal segmentPrice,
            Long remainingSeats,
            LocalDateTime departureDate
    ) {
        this(tripId, driverName,
                new VehicleSummary(vehicleId, brand, model, color, licensePlate, capacity),
                pickupCity, pickupTime, dropoffCity, dropoffTime, segmentPrice, remainingSeats, departureDate);
    }
}
