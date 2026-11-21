package org.multivehicle.covehicle.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.multivehicle.covehicle.dto.request.ReservationRequest;
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
import org.multivehicle.covehicle.mapper.ReservationMapper;
import org.multivehicle.covehicle.repository.PassengerRepository;
import org.multivehicle.covehicle.repository.ReservationRepository;
import org.multivehicle.covehicle.repository.StopRepository;
import org.multivehicle.covehicle.repository.TripRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceImplTest {

    private static final Long PASSENGER_ID = 7L;
    private static final Long TRIP_ID = 5L;
    private static final Long PICKUP_ID = 20L;
    private static final Long DROPOFF_ID = 21L;

    @Mock private ReservationRepository reservationRepository;
    @Mock private TripRepository tripRepository;
    @Mock private StopRepository stopRepository;
    @Mock private PassengerRepository passengerRepository;
    @Mock private ReservationMapper reservationMapper;

    @InjectMocks private ReservationServiceImpl reservationService;

    private static Passenger passenger(Long id) {
        return Passenger.builder()
                .id(id)
                .firstName("Salma")
                .lastName("Bennani")
                .email("salma" + id + "@x.com")
                .build();
    }

    private static Trip trip(int seatsOffered, TripStatus status) {
        return Trip.builder()
                .id(TRIP_ID)
                .seatsOffered(seatsOffered)
                .status(status)
                .departureDate(LocalDateTime.now().plusDays(2))
                .build();
    }

    private static Stop stop(Long id, String city, int order, String price, Trip trip) {
        return Stop.builder()
                .id(id)
                .city(city)
                .stopOrder(order)
                .arrivalTime(LocalDateTime.now().plusDays(2).plusHours(order))
                .priceFromStart(new BigDecimal(price))
                .trip(trip)
                .build();
    }

    private void stubBooking(Trip trip, Stop pickup, Stop dropoff, long reservedBefore, long reservedAfter,
                             boolean duplicate) {
        when(passengerRepository.findById(PASSENGER_ID)).thenReturn(Optional.of(passenger(PASSENGER_ID)));
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
        when(stopRepository.findById(PICKUP_ID)).thenReturn(Optional.of(pickup));
        when(stopRepository.findById(DROPOFF_ID)).thenReturn(Optional.of(dropoff));
        when(reservationRepository.existsByPassengerIdAndTripIdAndStatus(
                PASSENGER_ID, TRIP_ID, ReservationStatus.CONFIRMED)).thenReturn(duplicate);
        when(reservationRepository.sumReservedSeats(TRIP_ID, ReservationStatus.CONFIRMED))
                .thenReturn(reservedBefore, reservedAfter);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(reservationMapper.toResponse(any(Reservation.class), anyInt())).thenReturn(null);
    }

    private static ReservationRequest request(int seats) {
        return new ReservationRequest(TRIP_ID, PICKUP_ID, DROPOFF_ID, seats);
    }

    @Test
    void bookComputesSegmentPriceForRequestedSeats() {
        Trip trip = trip(3, TripStatus.PUBLISHED);
        Stop pickup = stop(PICKUP_ID, "Settat", 1, "30", trip);
        Stop dropoff = stop(DROPOFF_ID, "Marrakech", 2, "80", trip);
        stubBooking(trip, pickup, dropoff, 0, 2, false);

        reservationService.book(PASSENGER_ID, request(2));

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        Reservation saved = captor.getValue();

        assertThat(saved.getNumberOfSeats()).isEqualTo(2);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(saved.getPickupStop()).isSameAs(pickup);
        assertThat(saved.getDropoffStop()).isSameAs(dropoff);
        assertThat(saved.getTrip()).isSameAs(trip);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.PUBLISHED);
    }

    @Test
    void bookMarksTripFullWhenLastSeatsAreTaken() {
        Trip trip = trip(3, TripStatus.PUBLISHED);
        Stop pickup = stop(PICKUP_ID, "Settat", 1, "30", trip);
        Stop dropoff = stop(DROPOFF_ID, "Marrakech", 2, "80", trip);
        stubBooking(trip, pickup, dropoff, 0, 3, false);

        reservationService.book(PASSENGER_ID, request(3));

        assertThat(trip.getStatus()).isEqualTo(TripStatus.FULL);
    }

    @Test
    void bookRejectsMoreSeatsThanRemaining() {
        Trip trip = trip(3, TripStatus.PUBLISHED);
        Stop pickup = stop(PICKUP_ID, "Settat", 1, "30", trip);
        Stop dropoff = stop(DROPOFF_ID, "Marrakech", 2, "80", trip);
        stubBooking(trip, pickup, dropoff, 2, 5, false);

        assertThatThrownBy(() -> reservationService.book(PASSENGER_ID, request(2)))
                .isInstanceOf(InsufficientSeatsException.class)
                .hasMessageContaining("1 seats left");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void bookRejectsPickupAfterDropoff() {
        Trip trip = trip(3, TripStatus.PUBLISHED);
        Stop pickup = stop(PICKUP_ID, "Marrakech", 2, "80", trip);
        Stop dropoff = stop(DROPOFF_ID, "Settat", 1, "30", trip);
        stubBooking(trip, pickup, dropoff, 0, 0, false);

        assertThatThrownBy(() -> reservationService.book(PASSENGER_ID, request(1)))
                .isInstanceOf(InvalidSegmentException.class);
    }

    @Test
    void bookRejectsTripThatHasAlreadyDeparted() {
        Trip trip = trip(3, TripStatus.PUBLISHED);
        trip.setDepartureDate(LocalDateTime.now().minusMinutes(1));
        Stop pickup = stop(PICKUP_ID, "Settat", 1, "30", trip);
        Stop dropoff = stop(DROPOFF_ID, "Marrakech", 2, "80", trip);
        stubBooking(trip, pickup, dropoff, 0, 0, false);

        assertThatThrownBy(() -> reservationService.book(PASSENGER_ID, request(1)))
                .isInstanceOf(IllegalTripStateException.class)
                .hasMessageContaining("already departed");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void bookRejectsDuplicateConfirmedReservation() {
        Trip trip = trip(3, TripStatus.PUBLISHED);
        Stop pickup = stop(PICKUP_ID, "Settat", 1, "30", trip);
        Stop dropoff = stop(DROPOFF_ID, "Marrakech", 2, "80", trip);
        stubBooking(trip, pickup, dropoff, 0, 0, true);

        assertThatThrownBy(() -> reservationService.book(PASSENGER_ID, request(1)))
                .isInstanceOf(DuplicateReservationException.class);
    }

    @Test
    void bookRejectsTripThatIsNotPublished() {
        Trip trip = trip(3, TripStatus.FULL);
        Stop pickup = stop(PICKUP_ID, "Settat", 1, "30", trip);
        Stop dropoff = stop(DROPOFF_ID, "Marrakech", 2, "80", trip);
        stubBooking(trip, pickup, dropoff, 0, 0, false);

        assertThatThrownBy(() -> reservationService.book(PASSENGER_ID, request(1)))
                .isInstanceOf(IllegalTripStateException.class);
    }

    @Test
    void cancelFreesSeatsAndRestoresFullTripToPublished() {
        Trip trip = trip(3, TripStatus.FULL);
        trip.setDepartureDate(LocalDateTime.now().plusDays(2));
        Reservation reservation = Reservation.builder()
                .id(1L)
                .numberOfSeats(2)
                .totalAmount(new BigDecimal("100.00"))
                .status(ReservationStatus.CONFIRMED)
                .trip(trip)
                .passenger(passenger(PASSENGER_ID))
                .build();
        when(passengerRepository.findById(PASSENGER_ID)).thenReturn(Optional.of(passenger(PASSENGER_ID)));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));

        reservationService.cancel(PASSENGER_ID, 1L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.PUBLISHED);
    }

    @Test
    void cancelRejectsReservationInsideTwentyFourHourWindow() {
        Trip trip = trip(3, TripStatus.FULL);
        trip.setDepartureDate(LocalDateTime.now().plusHours(2));
        Reservation reservation = Reservation.builder()
                .id(1L)
                .numberOfSeats(2)
                .totalAmount(new BigDecimal("100.00"))
                .status(ReservationStatus.CONFIRMED)
                .trip(trip)
                .passenger(passenger(PASSENGER_ID))
                .build();
        when(passengerRepository.findById(PASSENGER_ID)).thenReturn(Optional.of(passenger(PASSENGER_ID)));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> reservationService.cancel(PASSENGER_ID, 1L))
                .isInstanceOf(CancellationTooLateException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.FULL);
    }

    @Test
    void cancelRejectsReservationOwnedByAnotherPassenger() {
        Reservation reservation = Reservation.builder()
                .id(1L)
                .status(ReservationStatus.CONFIRMED)
                .trip(trip(3, TripStatus.PUBLISHED))
                .passenger(passenger(99L))
                .build();
        when(passengerRepository.findById(PASSENGER_ID)).thenReturn(Optional.of(passenger(PASSENGER_ID)));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(PASSENGER_ID, 1L))
                .isInstanceOf(NotReservationOwnerException.class);
    }

    @Test
    void cancelRejectsAlreadyCanceledReservation() {
        Reservation reservation = Reservation.builder()
                .id(1L)
                .status(ReservationStatus.CANCELED)
                .trip(trip(3, TripStatus.PUBLISHED))
                .passenger(passenger(PASSENGER_ID))
                .build();
        when(passengerRepository.findById(PASSENGER_ID)).thenReturn(Optional.of(passenger(PASSENGER_ID)));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(PASSENGER_ID, 1L))
                .isInstanceOf(IllegalReservationStateException.class);
        verify(reservationRepository, never()).deleteById(eq(1L));
    }
}
