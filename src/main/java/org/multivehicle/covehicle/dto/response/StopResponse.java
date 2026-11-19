package org.multivehicle.covehicle.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StopResponse(
        Long id,
        String city,
        int stopOrder,
        LocalDateTime arrivalTime,
        BigDecimal priceFromStart
) {
}
