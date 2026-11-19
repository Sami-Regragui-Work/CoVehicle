package org.multivehicle.covehicle.dto.response;

public record VehicleSummary(
        Long id,
        String brand,
        String model,
        String color,
        String licensePlate,
        int capacity
) {
}
