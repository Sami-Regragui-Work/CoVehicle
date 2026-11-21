package org.multivehicle.covehicle.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.multivehicle.covehicle.dto.request.VehicleRequest;
import org.multivehicle.covehicle.dto.response.VehicleResponse;
import org.multivehicle.covehicle.entity.Vehicle;
import org.multivehicle.covehicle.entity.users.Driver;
import org.multivehicle.covehicle.exception.conflict.DuplicateResourceException;
import org.multivehicle.covehicle.exception.conflict.VehicleInUseException;
import org.multivehicle.covehicle.exception.forbidden.NotVehicleOwnerException;
import org.multivehicle.covehicle.exception.notfound.ResourceNotFoundException;
import org.multivehicle.covehicle.mapper.VehicleMapper;
import org.multivehicle.covehicle.repository.DriverRepository;
import org.multivehicle.covehicle.repository.TripRepository;
import org.multivehicle.covehicle.repository.VehicleRepository;
import org.multivehicle.covehicle.service.VehicleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;
    private final VehicleMapper vehicleMapper;

    @Override
    @Transactional
    public VehicleResponse create(Long driverId, VehicleRequest request) {
        Driver driver = findDriver(driverId);

        if (vehicleRepository.existsByLicensePlate(request.licensePlate())) {
            throw new DuplicateResourceException("License plate is already used: " + request.licensePlate());
        }

        Vehicle vehicle = vehicleMapper.toEntity(request);
        vehicle.setDriver(driver);

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Vehicle created with id {}", saved.getId());
        return vehicleMapper.toResponse(saved);
    }

    @Override
    public VehicleResponse getById(Long driverId, Long vehicleId) {
        ensureDriverExists(driverId);
        Vehicle vehicle = find(vehicleId);
        verifyOwnership(vehicle, driverId);
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponse update(Long driverId, Long vehicleId, VehicleRequest request) {
        ensureDriverExists(driverId);
        Vehicle vehicle = find(vehicleId);
        verifyOwnership(vehicle, driverId);

        if (vehicleRepository.existsByLicensePlateAndIdNot(request.licensePlate(), vehicleId)) {
            throw new DuplicateResourceException("License plate is already used: " + request.licensePlate());
        }

        vehicleMapper.updateEntity(request, vehicle);

        log.info("Vehicle updated with id {}", vehicleId);
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional
    public void delete(Long driverId, Long vehicleId) {
        ensureDriverExists(driverId);
        Vehicle vehicle = find(vehicleId);
        verifyOwnership(vehicle, driverId);

        if (tripRepository.existsByVehicleId(vehicleId)) {
            throw new VehicleInUseException();
        }

        vehicleRepository.delete(vehicle);
        log.info("Vehicle deleted with id {}", vehicleId);
    }

    @Override
    public List<VehicleResponse> listByDriver(Long driverId) {
        ensureDriverExists(driverId);
        return vehicleRepository.findByDriverId(driverId).stream()
                .map(vehicleMapper::toResponse)
                .toList();
    }

    private Vehicle find(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
    }

    private Driver findDriver(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));
    }

    private void ensureDriverExists(Long id) {
        if (!driverRepository.existsById(id)) {
            throw new ResourceNotFoundException("Driver not found with id: " + id);
        }
    }

    private void verifyOwnership(Vehicle vehicle, Long driverId) {
        if (!Objects.equals(vehicle.getDriver().getId(), driverId)) {
            throw new NotVehicleOwnerException();
        }
    }
}
