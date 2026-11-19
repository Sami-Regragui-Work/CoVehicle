package org.multivehicle.covehicle.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.multivehicle.covehicle.dto.request.PassengerRequest;
import org.multivehicle.covehicle.dto.response.PassengerResponse;
import org.multivehicle.covehicle.entity.users.Passenger;

@Mapper(componentModel = "spring")
public interface PassengerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    Passenger toEntity(PassengerRequest request);

    PassengerResponse toResponse(Passenger passenger);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntity(PassengerRequest request, @MappingTarget Passenger passenger);
}
