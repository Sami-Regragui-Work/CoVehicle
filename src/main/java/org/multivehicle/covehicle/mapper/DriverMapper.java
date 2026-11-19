package org.multivehicle.covehicle.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.multivehicle.covehicle.dto.request.DriverRequest;
import org.multivehicle.covehicle.dto.response.DriverResponse;
import org.multivehicle.covehicle.entity.users.Driver;

@Mapper(componentModel = "spring")
public interface DriverMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    Driver toEntity(DriverRequest request);

    DriverResponse toResponse(Driver driver);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntity(DriverRequest request, @MappingTarget Driver driver);
}
