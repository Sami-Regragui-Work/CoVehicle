package org.multivehicle.covehicle.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import org.multivehicle.covehicle.exception.notfound.ResourceNotFoundException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    private static final Long SALMA_ID = 7L;
    private static final Long KARIM_ID = 8L;
    private static final Long TRIP_ID = 5L;
    private static final Long PICKUP_ID = 20L;
    private static final Long DROPOFF_ID = 21L;

    @Mock private ReservationRepository reservationRepository;
    @Mock private TripRepository tripRepository;
    @Mock private StopRepository stopRepository;
    @Mock private PassengerRepository passengerRepository;
    @Mock private ReservationMapper reservationMapper;

    @InjectMocks private ReservationServiceImpl reservationService;

    private static Passenger passenger(Long id, String firstName, String lastName) {
        return Passenger.builder()
                .id(id)
                .firstName(firstName)
                .lastName(lastName)
                .email(firstName.toLowerCase() + id + "@x.com")
                .build();
    }

    private static Passenger salma() {
        return passenger(SALMA_ID, "Salma", "Bennani");
    }

    private static Passenger karim() {
        return passenger(KARIM_ID, "Karim", "Tazi");
    }

    private static Trip trip(int seatsOffered, TripStatus status) {
        return Trip.builder()
                .id(TRIP_ID)
                .seatsOffered(seatsOffered)
                .status(status)
                .departureDate(LocalDateTime.now().plusDays(3))
                .build();
    }

    private static Stop stop(Long id, String city, int order, String price, Trip trip) {
        return Stop.builder()
                .id(id)
                .city(city)
                .stopOrder(order)
                .arrivalTime(LocalDateTime.now().plusDays(3).plusHours(order))
                .priceFromStart(new BigDecimal(price))
                .trip(trip)
                .build();
    }

    private static ReservationRequest request(int seats) {
        return new ReservationRequest(TRIP_ID, PICKUP_ID, DROPOFF_ID, seats);
    }

    private void stubPassenger(Passenger passenger) {
        when(passengerRepository.findById(passenger.getId())).thenReturn(Optional.of(passenger));
    }

    private void stubTrip(Trip trip) {
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
    }

    private void stubStops(Stop pickup, Stop dropoff) {
        when(stopRepository.findById(PICKUP_ID)).thenReturn(Optional.of(pickup));
        when(stopRepository.findById(DROPOFF_ID)).thenReturn(Optional.of(dropoff));
    }

    private void stubDuplicateCheck(Long passengerId, boolean duplicate) {
        when(reservationRepository.existsByPassengerIdAndTripIdAndStatus(
                passengerId, TRIP_ID, ReservationStatus.CONFIRMED)).thenReturn(duplicate);
    }

    private void stubReservedBefore(long reservedBefore) {
        when(reservationRepository.sumReservedSeats(TRIP_ID, ReservationStatus.CONFIRMED))
                .thenReturn(reservedBefore);
    }

    private void stubReservedBeforeAfter(long reservedBefore, long reservedAfter) {
        when(reservationRepository.sumReservedSeats(TRIP_ID, ReservationStatus.CONFIRMED))
                .thenReturn(reservedBefore, reservedAfter);
    }

    private void stubSave() {
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            reservation.setId(1L);
            return reservation;
        });
    }

    private void assertRemainingSeats(int expected) {
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(reservationMapper).toResponse(any(Reservation.class), captor.capture());
        assertThat(captor.getValue()).isEqualTo(expected);
    }

    @Test
    void bookScenario1SalmaOneSeatFrom70To120() {
        Passenger salma = salma();
        Trip trip = trip(3, TripStatus.PUBLISHED);
        Stop pickup = stop(PICKUP_ID, "Settat", 1, "70", trip);
        Stop dropoff = stop(DROPOFF_ID, "Marrakech", 2, "120", trip);
        stubPassenger(salma);
        stubTrip(trip);
        stubStops(pickup, dropoff);
        stubDuplicateCheck(SALMA_ID, false);
        stubReservedBeforeAfter(0, 1);
        stubSave();

        reservationService.book(SALMA_ID, request(1));

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        Reservation saved = captor.getValue();

        assertThat(saved.getNumberOfSeats()).isEqualTo(1);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(saved.getPickupStop()).isSameAs(pickup);
        assertThat(saved.getDropoffStop()).isSameAs(dropoff);
        assertThat(saved.getTrip()).isSameAs(trip);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.PUBLISHED);
        assertRemainingSeats(2);
    }

    @Test
    void bookScenario3KarimTwoSeatsFrom40To120MarksTripFull() {
        Passenger karim = karim();
        Trip trip = trip(2, TripStatus.PUBLISHED);
        Stop pickup = stop(PICKUP_ID, "Casablanca", 0, "40", trip);
        Stop dropoff = stop(DROPOFF_ID, "Marrakech", 2, "120", trip);
        stubPassenger(karim);
        stubTrip(trip);
        stubStops(pickup, dropoff);
        stubDuplicateCheck(KARIM_ID, false);
        stubReservedBeforeAfter(0, 2);
        stubSave();

        reservationService.book(KARIM_ID, request(2));

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        Reservation saved = captor.getValue();

        assertThat(saved.getNumberOfSeats()).isEqualTo(2);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("160.00");
        assertThat(trip.getStatus()).isEqualTo(TripStatus.FULL);
        assertRemainingSeats(0);
    }

    @Test
    void bookRejectsUnknownPassenger() {
        when(passengerRepository.findById(SALMA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.book(SALMA_ID, request(1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Passenger not found");
    }

    @Test
    void bookRejectsUnknownTrip() {
        stubPassenger(salma());
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.book(SALMA_ID, request(1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Trip not found");
    }

    @Test
    void bookRejectsUnknownPickupStop() {
        stubPassenger(salma());
        stubTrip(trip(3, TripStatus.PUBLISHED));
        when(stopRepository.findById(PICKUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.book(SALMA_ID, request(1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Pickup stop not found");
    }

    @Test
    void bookRejectsUnknownDropoffStop() {
        Trip trip = trip(3, TripStatus.PUBLISHED);
        stubPassenger(salma());
        stubTrip(trip);
        when(stopRepository.findById(PICKUP_ID))
                .thenReturn(Optional.of(stop(PICKUP_ID, "Settat", 1, "70", trip)));
        when(stopRepository.findById(DROPOFF_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.book(SALMA_ID, request(1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Drop-off stop not found");
    }

    @Test
    void bookRejectsStopFromAnotherTrip() {
        Trip trip = trip(3, TripStatus.PUBLISHED);
        Trip otherTrip = Trip.builder().id(99L).build();
        Stop pickup = stop(PICKUP_ID, "Settat", 1, "70", otherTrip);
        Stop dropoff = stop(DROPOFF_ID, "Marrakech", 2, "120", trip);
        stubPassenger(salma());
        stubTrip(trip);
        stubStops(pickup, dropoff);

        assertThatThrownBy(() -> reservationService.book(SALMA_ID, request(1)))
                .isInstanceOf(InvalidSegmentException.class)
                .hasMessageContaining("must belong to the requested trip");
    }

    @Test
    void bookRejectsPickupAfterDropoff() {
        Trip trip = trip(3, TripStatus.PUBLISHED);
        Stop pickup = stop(PICKUP_ID, "Marrakech", 2, "120", trip);
        Stop dropoff = stop(DROPOFF_ID, "Settat", 1, "70", trip);
        stubPassenger(salma());
        stubTrip(trip);
        stubStops(pickup, dropoff);

        assertThatThrownBy(() -> reservationService.book(SALMA_ID, request(1)))
                .isInstanceOf(InvalidSegmentException.class);
    }

    @Test
    void bookScenario4RejectsBookingFullTripAndNeverSaves() {
        Trip trip = trip(2, TripStatus.FULL);
        Stop pickup = stop(PICKUP_ID, "Casablanca", 0, "40", trip);
        Stop dropoff = stop(DROPOFF_ID, "Marrakech", 2, "120", trip);
        stubPassenger(karim());
        stubTrip(trip);
        stubStops(pickup, dropoff);

        assertThatThrownBy(() -> reservationService.book(KARIM_ID, request(1)))
                .isInstanceOf(IllegalTripStateException.class)
                .hasMessageContaining("FULL");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void bookRejectsTripThatHasAlreadyDeparted() {
        Trip trip = trip(3, TripStatus.PUBLISHED);
        trip.setDepartureDate(LocalDateTime.now().minusMinutes(1));
        Stop pickup = stop(PICKUP_ID, "Settat", 1, "70", trip);
        Stop dropoff = stop(DROPOFF_ID, "Marrakech", 2, "120", trip);
        stubPassenger(salma());
        stubTrip(trip);
        stubStops(pickup, dropoff);

        assertThatThrownBy(() -> reservationService.book(SALMA_ID, request(1)))
                .isInstanceOf(IllegalTripStateException.class)
                .hasMessageContaining("already departed");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void bookRejectsDuplicateConfirmedReservation() {
        Trip trip = trip(3, TripStatus.PUBLISHED);
        Stop pickup = stop(PICKUP_ID, "Settat", 1, "70", trip);
        Stop dropoff = stop(DROPOFF_ID, "Marrakech", 2, "120", trip);
        stubPassenger(salma());
        stubTrip(trip);
        stubStops(pickup, dropoff);
        stubDuplicateCheck(SALMA_ID, true);

        assertThatThrownBy(() -> reservationService.book(SALMA_ID, request(1)))
                .isInstanceOf(DuplicateReservationException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void bookRejectsMoreSeatsThanRemainingAndNeverSaves() {
        Trip trip = trip(3, TripStatus.PUBLISHED);
        Stop pickup = stop(PICKUP_ID, "Settat", 1, "70", trip);
        Stop dropoff = stop(DROPOFF_ID, "Marrakech", 2, "120", trip);
        stubPassenger(salma());
        stubTrip(trip);
        stubStops(pickup, dropoff);
        stubDuplicateCheck(SALMA_ID, false);
        stubReservedBefore(2);

        assertThatThrownBy(() -> reservationService.book(SALMA_ID, request(2)))
                .isInstanceOf(InsufficientSeatsException.class)
                .hasMessageContaining("1 seats left");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void cancelFreesSeatsAndRestoresFullTripToPublished() {
        Trip trip = trip(3, TripStatus.FULL);
        Reservation reservation = Reservation.builder()
                .id(1L)
                .numberOfSeats(2)
                .totalAmount(new BigDecimal("100.00"))
                .status(ReservationStatus.CONFIRMED)
                .trip(trip)
                .passenger(salma())
                .build();
        when(passengerRepository.findById(SALMA_ID)).thenReturn(Optional.of(salma()));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));

        reservationService.cancel(SALMA_ID, 1L);

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
                .passenger(salma())
                .build();
        when(passengerRepository.findById(SALMA_ID)).thenReturn(Optional.of(salma()));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> reservationService.cancel(SALMA_ID, 1L))
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
                .passenger(passenger(99L, "Other", "Passenger"))
                .build();
        when(passengerRepository.findById(SALMA_ID)).thenReturn(Optional.of(salma()));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(SALMA_ID, 1L))
                .isInstanceOf(NotReservationOwnerException.class);
    }

    @Test
    void cancelRejectsAlreadyCanceledReservation() {
        Trip trip = trip(3, TripStatus.FULL);
        Reservation reservation = Reservation.builder()
                .id(1L)
                .status(ReservationStatus.CANCELED)
                .trip(trip)
                .passenger(salma())
                .build();
        when(passengerRepository.findById(SALMA_ID)).thenReturn(Optional.of(salma()));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(SALMA_ID, 1L))
                .isInstanceOf(IllegalReservationStateException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.FULL);
    }
}
