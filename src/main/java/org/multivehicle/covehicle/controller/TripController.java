package org.multivehicle.covehicle.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.multivehicle.covehicle.dto.request.TripRequest;
import org.multivehicle.covehicle.dto.response.ReservationResponse;
import org.multivehicle.covehicle.dto.response.TripResponse;
import org.multivehicle.covehicle.dto.response.TripSearchResult;
import org.multivehicle.covehicle.service.TripService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping("/api/drivers/{driverId}/trips")
    public ResponseEntity<TripResponse> publish(@PathVariable Long driverId, @Valid @RequestBody TripRequest request) {
        TripResponse created = tripService.publish(driverId, request);
        return ResponseEntity.created(URI.create("/api/trips/" + created.id())).body(created);
    }

    @GetMapping("/api/trips/search")
    public ResponseEntity<Page<TripSearchResult>> search(
            @RequestParam String departureCity,
            @RequestParam String arrivalCity,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") int seats,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(tripService.search(departureCity, arrivalCity, date, seats, maxPrice, page, size));
    }

    @GetMapping("/api/trips/{id}")
    public ResponseEntity<TripResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tripService.getById(id));
    }

    @GetMapping("/api/drivers/{driverId}/trips")
    public ResponseEntity<List<TripResponse>> listByDriver(@PathVariable Long driverId) {
        return ResponseEntity.ok(tripService.listByDriver(driverId));
    }

    @GetMapping("/api/drivers/{driverId}/trips/{tripId}/reservations")
    public ResponseEntity<List<ReservationResponse>> listReservations(
            @PathVariable Long driverId, @PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.listReservations(driverId, tripId));
    }

    @PostMapping("/api/drivers/{driverId}/trips/{tripId}/complete")
    public ResponseEntity<Void> complete(@PathVariable Long driverId, @PathVariable Long tripId) {
        tripService.complete(driverId, tripId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/drivers/{driverId}/trips/{tripId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long driverId, @PathVariable Long tripId) {
        tripService.cancel(driverId, tripId);
        return ResponseEntity.noContent().build();
    }
}
