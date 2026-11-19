package org.multivehicle.covehicle.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.multivehicle.covehicle.dto.request.DriverRequest;
import org.multivehicle.covehicle.dto.response.DriverResponse;
import org.multivehicle.covehicle.entity.users.Driver;
import org.multivehicle.covehicle.exception.conflict.DuplicateResourceException;
import org.multivehicle.covehicle.exception.notfound.ResourceNotFoundException;
import org.multivehicle.covehicle.mapper.DriverMapper;
import org.multivehicle.covehicle.repository.DriverRepository;
import org.multivehicle.covehicle.repository.UserRepository;
import org.multivehicle.covehicle.service.DriverService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final DriverMapper driverMapper;

    @Override
    @Transactional
    public DriverResponse create(DriverRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email is already used: " + request.email());
        }
        if (driverRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new DuplicateResourceException("License number is already used: " + request.licenseNumber());
        }

        Driver driver = driverRepository.save(driverMapper.toEntity(request));
        log.info("Driver created with id {}", driver.getId());
        return driverMapper.toResponse(driver);
    }

    @Override
    public DriverResponse getById(Long id) {
        return driverMapper.toResponse(find(id));
    }

    @Override
    public List<DriverResponse> getAll() {
        return driverRepository.findAll().stream()
                .map(driverMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public DriverResponse update(Long id, DriverRequest request) {
        Driver driver = find(id);

        if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new DuplicateResourceException("Email is already used: " + request.email());
        }
        if (driverRepository.existsByLicenseNumberAndIdNot(request.licenseNumber(), id)) {
            throw new DuplicateResourceException("License number is already used: " + request.licenseNumber());
        }

        driverMapper.updateEntity(request, driver);
        log.info("Driver updated with id {}", id);
        return driverMapper.toResponse(driver);
    }

    @Override
    public List<DriverResponse> search(String query) {
        return driverRepository.search(query).stream()
                .map(driverMapper::toResponse)
                .toList();
    }

    private Driver find(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));
    }
}
