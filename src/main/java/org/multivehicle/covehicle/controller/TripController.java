package org.multivehicle.covehicle.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.multivehicle.covehicle.dto.request.TripRequest;
import org.multivehicle.covehicle.dto.response.TripResponse;
import org.multivehicle.covehicle.service.TripService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping("/api/drivers/{driverId}/trips")
    public ResponseEntity<TripResponse> publish(@PathVariable Long driverId, @Valid @RequestBody TripRequest request) {
        TripResponse created = tripService.publish(driverId, request);
        return ResponseEntity.created(URI.create("/api/trips/" + created.id())).body(created);
    }

    @GetMapping("/api/trips/{id}")
    public ResponseEntity<TripResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tripService.getById(id));
    }
}
