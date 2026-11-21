package org.multivehicle.covehicle.dto.response;

import org.multivehicle.covehicle.entity.enums.ReservationStatus;
import org.multivehicle.covehicle.entity.enums.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long tripId,
        Long passengerId,
        String passengerName,
        String pickupCity,
        String dropoffCity,
        int numberOfSeats,
        BigDecimal totalAmount,
        LocalDateTime reservationDate,
        ReservationStatus status,
        TripStatus tripStatus,
        int remainingSeats
) {
}
