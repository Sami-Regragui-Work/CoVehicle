package org.multivehicle.covehicle.repository;

import org.multivehicle.covehicle.entity.Reservation;
import org.multivehicle.covehicle.entity.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("""
            SELECT COALESCE(SUM(r.numberOfSeats), 0)
            FROM Reservation r
            WHERE r.trip.id = :tripId AND r.status = :status
            """)
    long sumReservedSeats(@Param("tripId") Long tripId, @Param("status") ReservationStatus status);
}
