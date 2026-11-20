package org.multivehicle.covehicle.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.multivehicle.covehicle.dto.request.StopRequest;
import org.multivehicle.covehicle.dto.request.TripRequest;
import org.multivehicle.covehicle.dto.response.TripResponse;
import org.multivehicle.covehicle.dto.response.TripSearchResult;
import org.multivehicle.covehicle.entity.Stop;
import org.multivehicle.covehicle.entity.Trip;
import org.multivehicle.covehicle.entity.Vehicle;
import org.multivehicle.covehicle.entity.enums.ReservationStatus;
import org.multivehicle.covehicle.entity.enums.TripStatus;
import org.multivehicle.covehicle.exception.badrequest.InvalidSearchCriteriaException;
import org.multivehicle.covehicle.exception.badrequest.InvalidStopSequenceException;
import org.multivehicle.covehicle.exception.badrequest.SeatsExceedCapacityException;
import org.multivehicle.covehicle.exception.forbidden.NotVehicleOwnerException;
import org.multivehicle.covehicle.exception.notfound.ResourceNotFoundException;
import org.multivehicle.covehicle.mapper.StopMapper;
import org.multivehicle.covehicle.mapper.TripMapper;
import org.multivehicle.covehicle.repository.DriverRepository;
import org.multivehicle.covehicle.repository.ReservationRepository;
import org.multivehicle.covehicle.repository.TripRepository;
import org.multivehicle.covehicle.repository.VehicleRepository;
import org.multivehicle.covehicle.service.TripService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final ReservationRepository reservationRepository;
    private final TripMapper tripMapper;
    private final StopMapper stopMapper;

    @Override
    @Transactional
    public TripResponse publish(Long driverId, TripRequest request) {
        if (!driverRepository.existsById(driverId)) {
            throw new ResourceNotFoundException("Driver not found with id: " + driverId);
        }

        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.vehicleId()));

        if (!Objects.equals(vehicle.getDriver().getId(), driverId)) {
            throw new NotVehicleOwnerException();
        }

        int seatsOffered = request.seatsOffered();
        if (seatsOffered > vehicle.getCapacity()) {
            throw new SeatsExceedCapacityException(vehicle.getCapacity() + " places au maximum");
        }

        validateStops(request.stops());

        Trip trip = tripMapper.toEntity(request);
        trip.setVehicle(vehicle);

        List<StopRequest> stopRequests = request.stops();
        for (int i = 0; i < stopRequests.size(); i++) {
            Stop stop = stopMapper.toEntity(stopRequests.get(i));
            stop.setCity(stop.getCity().trim());
            stop.setStopOrder(i);
            trip.addStop(stop);
        }

        Trip saved = tripRepository.save(trip);
        log.info("Trip published with id {} by driver {}", saved.getId(), driverId);
        return tripMapper.toResponse(saved, saved.getSeatsOffered());
    }

    @Override
    public TripResponse getById(Long id) {
        Trip trip = tripRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + id));

        long reservedSeats = reservationRepository.sumReservedSeats(id, ReservationStatus.CONFIRMED);
        int remainingSeats = trip.getSeatsOffered() - Math.toIntExact(reservedSeats);
        return tripMapper.toResponse(trip, remainingSeats);
    }

    @Override
    public Page<TripSearchResult> search(String departureCity, String arrivalCity, LocalDate date,
                                         int seats, BigDecimal maxPrice, int page, int size) {
        if (seats < 1) {
            throw new InvalidSearchCriteriaException("seats must be at least 1");
        }
        if (page < 0) {
            throw new InvalidSearchCriteriaException("page must be 0 or greater");
        }
        if (size < 1) {
            throw new InvalidSearchCriteriaException("size must be at least 1");
        }
        if (size > 50) {
            throw new InvalidSearchCriteriaException("size must be at most 50");
        }

        String rawDeparture = departureCity == null ? "" : departureCity.trim();
        if (rawDeparture.isBlank()) {
            throw new InvalidSearchCriteriaException("departureCity must not be blank");
        }
        String rawArrival = arrivalCity == null ? "" : arrivalCity.trim();
        if (rawArrival.isBlank()) {
            throw new InvalidSearchCriteriaException("arrivalCity must not be blank");
        }
        String departure = rawDeparture.toLowerCase(Locale.ROOT);
        String arrival = rawArrival.toLowerCase(Locale.ROOT);

        LocalDateTime startDate = date.atStartOfDay();
        LocalDateTime endDate = startDate.plusDays(1);
        Pageable pageable = PageRequest.of(page, size);

        if (maxPrice == null) {
            Page<TripSearchResult> result = tripRepository.search(departure, arrival, startDate, endDate, seats,
                    TripStatus.PUBLISHED, ReservationStatus.CONFIRMED, pageable);
            return withForcedCount(result, pageable,
                    () -> tripRepository.countSearch(departure, arrival, startDate, endDate, seats,
                            TripStatus.PUBLISHED, ReservationStatus.CONFIRMED));
        }
        Page<TripSearchResult> result = tripRepository.searchWithMaxPrice(departure, arrival, startDate, endDate,
                seats, maxPrice, TripStatus.PUBLISHED, ReservationStatus.CONFIRMED, pageable);
        return withForcedCount(result, pageable,
                () -> tripRepository.countSearchWithMaxPrice(departure, arrival, startDate, endDate, seats, maxPrice,
                        TripStatus.PUBLISHED, ReservationStatus.CONFIRMED));
    }

    private Page<TripSearchResult> withForcedCount(Page<TripSearchResult> page, Pageable pageable,
                                                   java.util.function.LongSupplier countSupplier) {
        if (page.getContent().size() >= pageable.getPageSize()) {
            return page;
        }
        return new PageImpl<>(page.getContent(), pageable, countSupplier.getAsLong());
    }

    private void validateStops(List<StopRequest> stops) {
        if (stops.size() < 2) {
            throw new InvalidStopSequenceException("A trip must have at least 2 stops");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!stops.getFirst().arrivalTime().isAfter(now)) {
            throw new InvalidStopSequenceException("The first stop's arrival time must be in the future");
        }

        for (int i = 1; i < stops.size(); i++) {
            if (!stops.get(i).arrivalTime().isAfter(stops.get(i - 1).arrivalTime())) {
                throw new InvalidStopSequenceException("Stop arrival times must be strictly increasing");
            }
        }

        Set<String> cities = new HashSet<>();
        for (StopRequest stop : stops) {
            String city = stop.city().trim().toLowerCase(Locale.ROOT);
            if (!cities.add(city)) {
                throw new InvalidStopSequenceException("Stop cities must be unique: " + stop.city().trim());
            }
        }

        if (stops.getFirst().priceFromStart().compareTo(BigDecimal.ZERO) != 0) {
            throw new InvalidStopSequenceException("The first stop price must be 0");
        }

        for (int i = 1; i < stops.size(); i++) {
            if (stops.get(i).priceFromStart().compareTo(stops.get(i - 1).priceFromStart()) <= 0) {
                throw new InvalidStopSequenceException("Stop prices must be strictly increasing");
            }
        }
    }
}
