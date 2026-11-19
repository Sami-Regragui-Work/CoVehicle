package org.multivehicle.covehicle.dto.response;

public record PassengerResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone
) {
}
