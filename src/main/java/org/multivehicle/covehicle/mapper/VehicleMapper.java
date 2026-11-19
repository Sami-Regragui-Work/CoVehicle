package org.multivehicle.covehicle.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.multivehicle.covehicle.dto.request.VehicleRequest;
import org.multivehicle.covehicle.dto.response.VehicleResponse;
import org.multivehicle.covehicle.entity.Vehicle;

@Mapper(componentModel = "spring")
public interface VehicleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "driver", ignore = true)
    Vehicle toEntity(VehicleRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "driver", ignore = true)
    void updateEntity(VehicleRequest request, @MappingTarget Vehicle vehicle);

    @Mapping(target = "driverId", source = "driver.id")
    @Mapping(target = "driverName", expression = "java(vehicle.getDriver().getFirstName() + \" \" + vehicle.getDriver().getLastName())")
    VehicleResponse toResponse(Vehicle vehicle);
}
