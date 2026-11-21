package org.multivehicle.covehicle.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.multivehicle.covehicle.dto.response.ReservationResponse;
import org.multivehicle.covehicle.entity.Reservation;

@Mapper(componentModel = "spring")
public interface ReservationMapper {

    @Mapping(target = "tripId", source = "reservation.trip.id")
    @Mapping(target = "passengerId", source = "reservation.passenger.id")
    @Mapping(target = "passengerName", expression = "java(reservation.getPassenger().getFirstName() + \" \" + reservation.getPassenger().getLastName())")
    @Mapping(target = "pickupCity", source = "reservation.pickupStop.city")
    @Mapping(target = "dropoffCity", source = "reservation.dropoffStop.city")
    @Mapping(target = "tripStatus", source = "reservation.trip.status")
    ReservationResponse toResponse(Reservation reservation, int remainingSeats);
}
