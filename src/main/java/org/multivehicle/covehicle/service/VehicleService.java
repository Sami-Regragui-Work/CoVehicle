package org.multivehicle.covehicle.service;

import org.multivehicle.covehicle.dto.request.VehicleRequest;
import org.multivehicle.covehicle.dto.response.VehicleResponse;

import java.util.List;

public interface VehicleService {

    VehicleResponse create(Long driverId, VehicleRequest request);

    VehicleResponse getById(Long driverId, Long vehicleId);

    VehicleResponse update(Long driverId, Long vehicleId, VehicleRequest request);

    List<VehicleResponse> listByDriver(Long driverId);

    void delete(Long driverId, Long vehicleId);
}
