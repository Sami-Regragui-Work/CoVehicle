package org.multivehicle.covehicle.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.multivehicle.covehicle.dto.request.ReservationRequest;
import org.multivehicle.covehicle.dto.response.ReservationResponse;
import org.multivehicle.covehicle.entity.Reservation;
import org.multivehicle.covehicle.entity.Stop;
import org.multivehicle.covehicle.entity.Trip;
import org.multivehicle.covehicle.entity.enums.ReservationStatus;
import org.multivehicle.covehicle.entity.enums.TripStatus;
import org.multivehicle.covehicle.entity.users.Passenger;
import org.multivehicle.covehicle.exception.badrequest.InvalidSegmentException;
import org.multivehicle.covehicle.exception.conflict.CancellationTooLateException;
import org.multivehicle.covehicle.exception.conflict.DuplicateReservationException;
import org.multivehicle.covehicle.exception.conflict.IllegalReservationStateException;
import org.multivehicle.covehicle.exception.conflict.IllegalTripStateException;
import org.multivehicle.covehicle.exception.conflict.InsufficientSeatsException;
import org.multivehicle.covehicle.exception.forbidden.NotReservationOwnerException;
import org.multivehicle.covehicle.exception.notfound.ResourceNotFoundException;
import org.multivehicle.covehicle.mapper.ReservationMapper;
import org.multivehicle.covehicle.repository.PassengerRepository;
import org.multivehicle.covehicle.repository.ReservationRepository;
import org.multivehicle.covehicle.repository.StopRepository;
import org.multivehicle.covehicle.repository.TripRepository;
import org.multivehicle.covehicle.service.ReservationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final TripRepository tripRepository;
    private final StopRepository stopRepository;
    private final PassengerRepository passengerRepository;
    private final ReservationMapper reservationMapper;

    @Override
    @Transactional
    public ReservationResponse book(Long passengerId, ReservationRequest request) {
        Passenger passenger = findPassenger(passengerId);

        Trip trip = tripRepository.findByIdForUpdate(request.tripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + request.tripId()));
        Stop pickupStop = stopRepository.findById(request.pickupStopId())
                .orElseThrow(() -> new ResourceNotFoundException("Pickup stop not found with id: " + request.pickupStopId()));
        Stop dropoffStop = stopRepository.findById(request.dropoffStopId())
                .orElseThrow(() -> new ResourceNotFoundException("Drop-off stop not found with id: " + request.dropoffStopId()));

        if (!pickupStop.getTrip().getId().equals(trip.getId())
                || !dropoffStop.getTrip().getId().equals(trip.getId())) {
            throw new InvalidSegmentException("Pick-up and drop-off stops must belong to the requested trip");
        }
        if (pickupStop.getStopOrder() >= dropoffStop.getStopOrder()) {
            throw new InvalidSegmentException();
        }
        if (trip.getStatus() != TripStatus.PUBLISHED) {
            throw new IllegalTripStateException("Trip is " + trip.getStatus() + " and cannot be booked");
        }
        if (!trip.getDepartureDate().isAfter(LocalDateTime.now())) {
            throw new IllegalTripStateException("Trip has already departed and cannot be booked");
        }
        if (reservationRepository.existsByPassengerIdAndTripIdAndStatus(
                passengerId, trip.getId(), ReservationStatus.CONFIRMED)) {
            throw new DuplicateReservationException();
        }

        int remaining = remainingSeats(trip);
        int seats = request.numberOfSeats();
        if (seats > remaining) {
            throw new InsufficientSeatsException(remaining + " seats left on this trip");
        }

        BigDecimal unitPrice = dropoffStop.getPriceFromStart().subtract(pickupStop.getPriceFromStart());
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(seats)).setScale(2, RoundingMode.HALF_UP);

        Reservation reservation = Reservation.builder()
                .numberOfSeats(seats)
                .totalAmount(totalAmount)
                .pickupStop(pickupStop)
                .dropoffStop(dropoffStop)
                .trip(trip)
                .passenger(passenger)
                .build();

        Reservation saved = reservationRepository.save(reservation);

        int remainingAfter = remainingSeats(trip);
        if (remainingAfter == 0) {
            trip.setStatus(TripStatus.FULL);
        }

        log.info("Reservation {} created by passenger {} on trip {}", saved.getId(), passengerId, trip.getId());
        return reservationMapper.toResponse(saved, remainingAfter);
    }

    @Override
    @Transactional
    public void cancel(Long passengerId, Long reservationId) {
        findPassenger(passengerId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + reservationId));

        if (!reservation.getPassenger().getId().equals(passengerId)) {
            throw new NotReservationOwnerException();
        }
        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            throw new IllegalReservationStateException();
        }

        Trip trip = tripRepository.findByIdForUpdate(reservation.getTrip().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trip not found with id: " + reservation.getTrip().getId()));
        if (LocalDateTime.now().isAfter(trip.getDepartureDate().minusHours(24))) {
            throw new CancellationTooLateException();
        }

        reservation.setStatus(ReservationStatus.CANCELED);
        if (trip.getStatus() == TripStatus.FULL) {
            trip.setStatus(TripStatus.PUBLISHED);
        }

        log.info("Reservation {} canceled by passenger {}", reservationId, passengerId);
    }

    @Override
    public List<ReservationResponse> listByPassenger(Long passengerId) {
        if (!passengerRepository.existsById(passengerId)) {
            throw new ResourceNotFoundException("Passenger not found with id: " + passengerId);
        }

        List<Reservation> reservations = reservationRepository.findHistory(passengerId);
        Map<Long, Integer> remainingByTrip = new HashMap<>();

        return reservations.stream()
                .map(reservation -> {
                    Trip trip = reservation.getTrip();
                    int remaining = remainingByTrip.computeIfAbsent(trip.getId(), id -> {
                        long reserved = reservationRepository.sumReservedSeats(id, ReservationStatus.CONFIRMED);
                        return trip.getSeatsOffered() - Math.toIntExact(reserved);
                    });
                    return reservationMapper.toResponse(reservation, remaining);
                })
                .toList();
    }

    private Passenger findPassenger(Long passengerId) {
        return passengerRepository.findById(passengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger not found with id: " + passengerId));
    }

    private int remainingSeats(Trip trip) {
        long reserved = reservationRepository.sumReservedSeats(trip.getId(), ReservationStatus.CONFIRMED);
        return trip.getSeatsOffered() - Math.toIntExact(reserved);
    }
}
