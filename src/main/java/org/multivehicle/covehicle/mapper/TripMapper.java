package org.multivehicle.covehicle.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.multivehicle.covehicle.dto.request.TripRequest;
import org.multivehicle.covehicle.dto.response.TripResponse;
import org.multivehicle.covehicle.entity.Trip;

@Mapper(componentModel = "spring", uses = StopMapper.class)
public interface TripMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "vehicle", ignore = true)
    @Mapping(target = "departureDate", ignore = true)
    @Mapping(target = "stops", ignore = true)
    Trip toEntity(TripRequest request);

    @Mapping(target = "driverId", source = "trip.vehicle.driver.id")
    @Mapping(target = "driverName", expression = "java(trip.getVehicle().getDriver().getFirstName() + \" \" + trip.getVehicle().getDriver().getLastName())")
    TripResponse toResponse(Trip trip, int remainingSeats);
}
