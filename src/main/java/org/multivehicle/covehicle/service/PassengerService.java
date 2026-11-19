package org.multivehicle.covehicle.service;

import org.multivehicle.covehicle.dto.request.PassengerRequest;
import org.multivehicle.covehicle.dto.response.PassengerResponse;

import java.util.List;

public interface PassengerService {

    PassengerResponse create(PassengerRequest request);

    PassengerResponse getById(Long id);

    List<PassengerResponse> getAll();

    PassengerResponse update(Long id, PassengerRequest request);

    PassengerResponse findByEmail(String email);
}
