package org.multivehicle.covehicle.service;

import org.multivehicle.covehicle.dto.request.DriverRequest;
import org.multivehicle.covehicle.dto.response.DriverResponse;

import java.util.List;

public interface DriverService {

    DriverResponse create(DriverRequest request);

    DriverResponse getById(Long id);

    List<DriverResponse> getAll();

    DriverResponse update(Long id, DriverRequest request);

    List<DriverResponse> search(String query);
}
