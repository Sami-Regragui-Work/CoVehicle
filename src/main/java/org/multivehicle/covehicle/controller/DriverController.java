package org.multivehicle.covehicle.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.multivehicle.covehicle.dto.request.DriverRequest;
import org.multivehicle.covehicle.dto.response.DriverResponse;
import org.multivehicle.covehicle.service.DriverService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @PostMapping
    public ResponseEntity<DriverResponse> create(@Valid @RequestBody DriverRequest request) {
        DriverResponse created = driverService.create(request);
        return ResponseEntity.created(URI.create("/api/drivers/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DriverResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(driverService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<DriverResponse>> getAll() {
        return ResponseEntity.ok(driverService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<DriverResponse> update(@PathVariable Long id, @Valid @RequestBody DriverRequest request) {
        return ResponseEntity.ok(driverService.update(id, request));
    }

    @GetMapping("/search")
    public ResponseEntity<List<DriverResponse>> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(driverService.search(query));
    }
}
