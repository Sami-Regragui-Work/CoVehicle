package org.multivehicle.covehicle.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.multivehicle.covehicle.dto.request.PassengerRequest;
import org.multivehicle.covehicle.dto.response.PassengerResponse;
import org.multivehicle.covehicle.service.PassengerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/passengers")
@RequiredArgsConstructor
public class PassengerController {

    private final PassengerService passengerService;

    @PostMapping
    public ResponseEntity<PassengerResponse> create(@Valid @RequestBody PassengerRequest request) {
        PassengerResponse created = passengerService.create(request);
        return ResponseEntity.created(URI.create("/api/passengers/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PassengerResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(passengerService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<PassengerResponse>> getAll() {
        return ResponseEntity.ok(passengerService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<PassengerResponse> update(@PathVariable Long id, @Valid @RequestBody PassengerRequest request) {
        return ResponseEntity.ok(passengerService.update(id, request));
    }

    @GetMapping("/by-email")
    public ResponseEntity<PassengerResponse> findByEmail(@RequestParam String email) {
        return ResponseEntity.ok(passengerService.findByEmail(email));
    }
}
