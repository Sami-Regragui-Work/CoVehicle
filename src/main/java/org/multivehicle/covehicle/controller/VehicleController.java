package org.multivehicle.covehicle.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.multivehicle.covehicle.dto.request.VehicleRequest;
import org.multivehicle.covehicle.dto.response.VehicleResponse;
import org.multivehicle.covehicle.service.VehicleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/drivers/{driverId}/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    public ResponseEntity<VehicleResponse> create(@PathVariable Long driverId, @Valid @RequestBody VehicleRequest request) {
        VehicleResponse created = vehicleService.create(driverId, request);
        return ResponseEntity.created(URI.create("/api/drivers/" + driverId + "/vehicles/" + created.id())).body(created);
    }

    @GetMapping
    public ResponseEntity<List<VehicleResponse>> listByDriver(@PathVariable Long driverId) {
        return ResponseEntity.ok(vehicleService.listByDriver(driverId));
    }

    @GetMapping("/{vehicleId}")
    public ResponseEntity<VehicleResponse> getById(@PathVariable Long driverId, @PathVariable Long vehicleId) {
        return ResponseEntity.ok(vehicleService.getById(driverId, vehicleId));
    }

    @PutMapping("/{vehicleId}")
    public ResponseEntity<VehicleResponse> update(@PathVariable Long driverId, @PathVariable Long vehicleId, @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.ok(vehicleService.update(driverId, vehicleId, request));
    }
}
