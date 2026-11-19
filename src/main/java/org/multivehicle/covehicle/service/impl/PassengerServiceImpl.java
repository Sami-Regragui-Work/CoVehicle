package org.multivehicle.covehicle.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.multivehicle.covehicle.dto.request.PassengerRequest;
import org.multivehicle.covehicle.dto.response.PassengerResponse;
import org.multivehicle.covehicle.entity.users.Passenger;
import org.multivehicle.covehicle.exception.conflict.DuplicateResourceException;
import org.multivehicle.covehicle.exception.notfound.ResourceNotFoundException;
import org.multivehicle.covehicle.mapper.PassengerMapper;
import org.multivehicle.covehicle.repository.PassengerRepository;
import org.multivehicle.covehicle.repository.UserRepository;
import org.multivehicle.covehicle.service.PassengerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PassengerServiceImpl implements PassengerService {

    private final PassengerRepository passengerRepository;
    private final UserRepository userRepository;
    private final PassengerMapper passengerMapper;

    @Override
    @Transactional
    public PassengerResponse create(PassengerRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email is already used: " + request.email());
        }

        Passenger passenger = passengerRepository.save(passengerMapper.toEntity(request));
        log.info("Passenger created with id {}", passenger.getId());
        return passengerMapper.toResponse(passenger);
    }

    @Override
    public PassengerResponse getById(Long id) {
        return passengerMapper.toResponse(find(id));
    }

    @Override
    public List<PassengerResponse> getAll() {
        return passengerRepository.findAll().stream()
                .map(passengerMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public PassengerResponse update(Long id, PassengerRequest request) {
        Passenger passenger = find(id);

        if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new DuplicateResourceException("Email is already used: " + request.email());
        }

        passengerMapper.updateEntity(request, passenger);
        log.info("Passenger updated with id {}", id);
        return passengerMapper.toResponse(passenger);
    }

    @Override
    public PassengerResponse findByEmail(String email) {
        Passenger passenger = passengerRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger not found with email: " + email));
        return passengerMapper.toResponse(passenger);
    }

    private Passenger find(Long id) {
        return passengerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger not found with id: " + id));
    }
}
