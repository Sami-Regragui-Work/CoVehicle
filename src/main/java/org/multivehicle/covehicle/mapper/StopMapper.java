package org.multivehicle.covehicle.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.multivehicle.covehicle.dto.request.StopRequest;
import org.multivehicle.covehicle.dto.response.StopResponse;
import org.multivehicle.covehicle.entity.Stop;

@Mapper(componentModel = "spring")
public interface StopMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "trip", ignore = true)
    @Mapping(target = "stopOrder", ignore = true)
    Stop toEntity(StopRequest request);

    StopResponse toResponse(Stop stop);
}
