package org.multivehicle.covehicle.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.multivehicle.covehicle.dto.request.ReservationRequest;
import org.multivehicle.covehicle.dto.response.ReservationResponse;
import org.multivehicle.covehicle.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/passengers/{passengerId}/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> book(@PathVariable Long passengerId,
                                                    @Valid @RequestBody ReservationRequest request) {
        ReservationResponse created = reservationService.book(passengerId, request);
        return ResponseEntity.created(URI.create("/api/passengers/" + passengerId + "/reservations/" + created.id()))
                .body(created);
    }

    @DeleteMapping("/{reservationId}")
    public ResponseEntity<Void> cancel(@PathVariable Long passengerId, @PathVariable Long reservationId) {
        reservationService.cancel(passengerId, reservationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> listByPassenger(@PathVariable Long passengerId) {
        return ResponseEntity.ok(reservationService.listByPassenger(passengerId));
    }
}
