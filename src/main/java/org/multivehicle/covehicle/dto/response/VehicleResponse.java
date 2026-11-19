package org.multivehicle.covehicle.dto.response;

public record VehicleResponse(
        Long id,
        String brand,
        String model,
        String color,
        String licensePlate,
        int capacity,
        Long driverId,
        String driverName
) {
}
