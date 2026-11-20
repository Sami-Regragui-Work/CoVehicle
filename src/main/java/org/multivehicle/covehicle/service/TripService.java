package org.multivehicle.covehicle.service;

import org.multivehicle.covehicle.dto.request.TripRequest;
import org.multivehicle.covehicle.dto.response.TripResponse;
import org.multivehicle.covehicle.dto.response.TripSearchResult;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface TripService {

    TripResponse publish(Long driverId, TripRequest request);

    TripResponse getById(Long id);

    Page<TripSearchResult> search(String departureCity, String arrivalCity, LocalDate date,
                                  int seats, BigDecimal maxPrice, int page, int size);
}
