package org.multivehicle.covehicle.service;

import org.multivehicle.covehicle.dto.request.ReservationRequest;
import org.multivehicle.covehicle.dto.response.ReservationResponse;

import java.util.List;

public interface ReservationService {

    ReservationResponse book(Long passengerId, ReservationRequest request);

    void cancel(Long passengerId, Long reservationId);

    List<ReservationResponse> listByPassenger(Long passengerId);
}
