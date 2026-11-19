package org.multivehicle.covehicle.service;

import org.multivehicle.covehicle.dto.request.TripRequest;
import org.multivehicle.covehicle.dto.response.TripResponse;

public interface TripService {

    TripResponse publish(Long driverId, TripRequest request);

    TripResponse getById(Long id);
}
