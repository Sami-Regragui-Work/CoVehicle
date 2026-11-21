package org.multivehicle.covehicle.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.multivehicle.covehicle.dto.request.StopRequest;
import org.multivehicle.covehicle.dto.request.TripRequest;
import org.multivehicle.covehicle.entity.Stop;
import org.multivehicle.covehicle.entity.Trip;
import org.multivehicle.covehicle.entity.Vehicle;
import org.multivehicle.covehicle.entity.enums.TripStatus;
import org.multivehicle.covehicle.entity.users.Driver;
import org.multivehicle.covehicle.exception.badrequest.InvalidStopSequenceException;
import org.multivehicle.covehicle.exception.badrequest.SeatsExceedCapacityException;
import org.multivehicle.covehicle.exception.conflict.IllegalTripStateException;
import org.multivehicle.covehicle.exception.conflict.TripNotStartedException;
import org.multivehicle.covehicle.exception.forbidden.NotTripOwnerException;
import org.multivehicle.covehicle.exception.forbidden.NotVehicleOwnerException;
import org.multivehicle.covehicle.mapper.ReservationMapper;
import org.multivehicle.covehicle.mapper.StopMapper;
import org.multivehicle.covehicle.mapper.TripMapper;
import org.multivehicle.covehicle.repository.DriverRepository;
import org.multivehicle.covehicle.repository.ReservationRepository;
import org.multivehicle.covehicle.repository.TripRepository;
import org.multivehicle.covehicle.repository.VehicleRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripServiceImplTest {

    private static final Long DRIVER_ID = 5L;
    private static final Long VEHICLE_ID = 4L;

    @Mock private TripRepository tripRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private TripMapper tripMapper;
    @Mock private StopMapper stopMapper;
    @Mock private ReservationMapper reservationMapper;

    @InjectMocks private TripServiceImpl tripService;

    private static Driver driver(Long id) {
        return Driver.builder()
                .id(id)
                .firstName("Hamid")
                .lastName("Alami")
                .email("hamid" + id + "@x.com")
                .licenseNumber("LIC-" + id)
                .build();
    }

    private static Vehicle vehicle(Long id, Driver driver, int capacity) {
        return Vehicle.builder()
                .id(id)
                .brand("Mercedes")
                .model("Vito")
                .licensePlate("PLATE-" + id)
                .capacity(capacity)
                .driver(driver)
                .build();
    }

    private static TripRequest request(int seats, List<StopRequest> stops) {
        return new TripRequest(VEHICLE_ID, seats, stops);
    }

    private static List<StopRequest> validStops() {
        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);
        return List.of(
                new StopRequest(" Casablanca ", start, BigDecimal.ZERO),
                new StopRequest("Settat", start.plusHours(1), new BigDecimal("30")),
                new StopRequest("Marrakech", start.plusHours(4), new BigDecimal("80"))
        );
    }

    private void stubPublishSuccess(int seats, List<StopRequest> stops) {
        Vehicle v = vehicle(VEHICLE_ID, driver(DRIVER_ID), 4);
        when(driverRepository.existsById(DRIVER_ID)).thenReturn(true);
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(v));
        when(tripMapper.toEntity(any(TripRequest.class))).thenReturn(Trip.builder().build());
        when(stopMapper.toEntity(any(StopRequest.class))).thenAnswer(invocation -> {
            StopRequest s = invocation.getArgument(0);
            return Stop.builder()
                    .city(s.city())
                    .arrivalTime(s.arrivalTime())
                    .priceFromStart(s.priceFromStart())
                    .build();
        });
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> {
            Trip t = invocation.getArgument(0);
            t.setId(42L);
            return t;
        });
        when(tripMapper.toResponse(any(Trip.class), anyInt())).thenReturn(null);
    }

    @Test
    void publishCreatesTripWithOrderedAndTrimmedStops() {
        List<StopRequest> stops = validStops();
        stubPublishSuccess(2, stops);

        tripService.publish(DRIVER_ID, request(2, stops));

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());
        Trip saved = captor.getValue();

        assertThat(saved.getVehicle().getId()).isEqualTo(VEHICLE_ID);
        assertThat(saved.getStops()).hasSize(3);
        assertThat(saved.getStops().getFirst().getCity()).isEqualTo("Casablanca");
        assertThat(saved.getStops()).extracting(Stop::getStopOrder).containsExactly(0, 1, 2);
        assertThat(saved.getStops()).allSatisfy(stop -> assertThat(stop.getTrip()).isSameAs(saved));
    }

    @Test
    void publishRejectsSeatsAboveVehicleCapacity() {
        Vehicle v = vehicle(VEHICLE_ID, driver(DRIVER_ID), 4);
        when(driverRepository.existsById(DRIVER_ID)).thenReturn(true);
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> tripService.publish(DRIVER_ID, request(5, validStops())))
                .isInstanceOf(SeatsExceedCapacityException.class);
        verify(tripRepository, never()).save(any());
    }

    @Test
    void publishRejectsVehicleOwnedByAnotherDriver() {
        Vehicle v = vehicle(VEHICLE_ID, driver(99L), 4);
        when(driverRepository.existsById(DRIVER_ID)).thenReturn(true);
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> tripService.publish(DRIVER_ID, request(2, validStops())))
                .isInstanceOf(NotVehicleOwnerException.class);
    }

    @Test
    void publishRejectsFewerThanTwoStops() {
        stubPublishSuccess(2, validStops());
        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);
        TripRequest oneStop = request(2, List.of(new StopRequest("Rabat", start, BigDecimal.ZERO)));

        assertThatThrownBy(() -> tripService.publish(DRIVER_ID, oneStop))
                .isInstanceOf(InvalidStopSequenceException.class)
                .hasMessageContaining("at least 2 stops");
    }

    @Test
    void publishRejectsPastDeparture() {
        stubPublishSuccess(2, validStops());
        LocalDateTime past = LocalDateTime.now().minusHours(1).withNano(0);
        TripRequest pastTrip = request(2, List.of(
                new StopRequest("Rabat", past, BigDecimal.ZERO),
                new StopRequest("Fes", past.plusHours(2), new BigDecimal("50"))
        ));

        assertThatThrownBy(() -> tripService.publish(DRIVER_ID, pastTrip))
                .isInstanceOf(InvalidStopSequenceException.class)
                .hasMessageContaining("future");
    }

    @Test
    void publishRejectsDuplicateCity() {
        stubPublishSuccess(2, validStops());
        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);
        TripRequest duplicate = request(2, List.of(
                new StopRequest("Rabat", start, BigDecimal.ZERO),
                new StopRequest("rabat", start.plusHours(2), new BigDecimal("50"))
        ));

        assertThatThrownBy(() -> tripService.publish(DRIVER_ID, duplicate))
                .isInstanceOf(InvalidStopSequenceException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void publishRejectsNonIncreasingPrices() {
        stubPublishSuccess(2, validStops());
        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);
        TripRequest prices = request(2, List.of(
                new StopRequest("Rabat", start, BigDecimal.ZERO),
                new StopRequest("Fes", start.plusHours(2), BigDecimal.ZERO)
        ));

        assertThatThrownBy(() -> tripService.publish(DRIVER_ID, prices))
                .isInstanceOf(InvalidStopSequenceException.class)
                .hasMessageContaining("prices must be strictly increasing");
    }

    @Test
    void completeRejectsNonOwner() {
        Trip trip = Trip.builder()
                .id(7L).status(TripStatus.PUBLISHED)
                .departureDate(LocalDateTime.now().minusHours(1))
                .vehicle(vehicle(VEHICLE_ID, driver(99L), 4))
                .build();
        when(tripRepository.findById(7L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.complete(DRIVER_ID, 7L))
                .isInstanceOf(NotTripOwnerException.class);
    }

    @Test
    void completeRejectsTripThatHasNotDeparted() {
        Trip trip = Trip.builder()
                .id(7L).status(TripStatus.PUBLISHED)
                .departureDate(LocalDateTime.now().plusHours(2))
                .vehicle(vehicle(VEHICLE_ID, driver(DRIVER_ID), 4))
                .build();
        when(tripRepository.findById(7L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.complete(DRIVER_ID, 7L))
                .isInstanceOf(TripNotStartedException.class);
    }

    @Test
    void completeMarksDepartedTripAsCompleted() {
        Trip trip = Trip.builder()
                .id(7L).status(TripStatus.PUBLISHED)
                .departureDate(LocalDateTime.now().minusHours(1))
                .vehicle(vehicle(VEHICLE_ID, driver(DRIVER_ID), 4))
                .build();
        when(tripRepository.findById(7L)).thenReturn(Optional.of(trip));

        tripService.complete(DRIVER_ID, 7L);

        assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
    }

    @Test
    void cancelCascadesToConfirmedReservations() {
        Trip trip = Trip.builder()
                .id(7L).status(TripStatus.FULL)
                .departureDate(LocalDateTime.now().plusHours(2))
                .vehicle(vehicle(VEHICLE_ID, driver(DRIVER_ID), 4))
                .build();
        when(tripRepository.findById(7L)).thenReturn(Optional.of(trip));
        when(reservationRepository.cancelConfirmedByTripId(7L)).thenReturn(2);

        tripService.cancel(DRIVER_ID, 7L);

        assertThat(trip.getStatus()).isEqualTo(TripStatus.CANCELED);
        verify(reservationRepository).cancelConfirmedByTripId(7L);
    }

    @Test
    void cancelRejectsTripInFinalState() {
        Trip trip = Trip.builder()
                .id(7L).status(TripStatus.COMPLETED)
                .departureDate(LocalDateTime.now().minusHours(1))
                .vehicle(vehicle(VEHICLE_ID, driver(DRIVER_ID), 4))
                .build();
        when(tripRepository.findById(7L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.cancel(DRIVER_ID, 7L))
                .isInstanceOf(IllegalTripStateException.class);
        verify(reservationRepository, never()).cancelConfirmedByTripId(any());
    }
}
