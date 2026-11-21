package org.multivehicle.covehicle.repository;

import org.multivehicle.covehicle.entity.Reservation;
import org.multivehicle.covehicle.entity.enums.ReservationStatus;
import org.multivehicle.covehicle.repository.projection.TripReservedSeats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("""
            SELECT COALESCE(SUM(r.numberOfSeats), 0)
            FROM Reservation r
            WHERE r.trip.id = :tripId AND r.status = :status
            """)
    long sumReservedSeats(@Param("tripId") Long tripId, @Param("status") ReservationStatus status);

    boolean existsByPassengerIdAndTripIdAndStatus(Long passengerId, Long tripId, ReservationStatus status);

    @Query("""
            SELECT new org.multivehicle.covehicle.repository.projection.TripReservedSeats(
                r.trip.id, SUM(r.numberOfSeats))
            FROM Reservation r
            JOIN r.trip t
            JOIN t.vehicle v
            WHERE v.driver.id = :driverId AND r.status = :status
            GROUP BY r.trip.id
            """)
    List<TripReservedSeats> sumReservedSeatsByDriver(
            @Param("driverId") Long driverId, @Param("status") ReservationStatus status);

    @Query("""
            SELECT r FROM Reservation r
            JOIN FETCH r.pickupStop
            JOIN FETCH r.dropoffStop
            JOIN FETCH r.trip
            JOIN FETCH r.passenger
            WHERE r.passenger.id = :passengerId
            ORDER BY r.reservationDate DESC, r.id DESC
            """)
    List<Reservation> findHistory(@Param("passengerId") Long passengerId);

    @Query("""
            SELECT r FROM Reservation r
            JOIN FETCH r.pickupStop
            JOIN FETCH r.dropoffStop
            JOIN FETCH r.trip t
            JOIN FETCH t.vehicle v
            JOIN FETCH v.driver
            JOIN FETCH r.passenger
            WHERE r.trip.id = :tripId
            ORDER BY r.reservationDate DESC, r.id DESC
            """)
    List<Reservation> findByTripIdWithDetails(@Param("tripId") Long tripId);

    @Modifying
    @Query("""
            UPDATE Reservation r
            SET r.status = org.multivehicle.covehicle.entity.enums.ReservationStatus.CANCELED
            WHERE r.trip.id = :tripId
              AND r.status = org.multivehicle.covehicle.entity.enums.ReservationStatus.CONFIRMED
            """)
    int cancelConfirmedByTripId(@Param("tripId") Long tripId);
}
