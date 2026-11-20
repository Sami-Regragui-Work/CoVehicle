package org.multivehicle.covehicle.repository;

import org.multivehicle.covehicle.dto.response.TripSearchResult;
import org.multivehicle.covehicle.entity.Trip;
import org.multivehicle.covehicle.entity.enums.ReservationStatus;
import org.multivehicle.covehicle.entity.enums.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    String SEARCH_PROJECTION = """
            SELECT new org.multivehicle.covehicle.dto.response.TripSearchResult(
                t.id,
                CONCAT(d.firstName, ' ', d.lastName),
                v.id, v.brand, v.model, v.color, v.licensePlate, v.capacity,
                pickup.city,
                pickup.arrivalTime,
                dropoff.city,
                dropoff.arrivalTime,
                (dropoff.priceFromStart - pickup.priceFromStart),
                (t.seatsOffered - COALESCE((SELECT SUM(r.numberOfSeats) FROM Reservation r WHERE r.trip = t AND r.status = :confirmedStatus), 0)),
                t.departureDate
            )
            """;

    String SEARCH_FROM_WHERE = """
            FROM Trip t
            JOIN t.vehicle v
            JOIN v.driver d
            JOIN t.stops pickup
            JOIN t.stops dropoff
            WHERE t.status = :publishedStatus
              AND LOWER(pickup.city) = :departureCity
              AND LOWER(dropoff.city) = :arrivalCity
              AND pickup.stopOrder < dropoff.stopOrder
              AND pickup.arrivalTime >= :startDate
              AND pickup.arrivalTime < :endDate
              AND (t.seatsOffered - COALESCE((SELECT SUM(r.numberOfSeats) FROM Reservation r WHERE r.trip = t AND r.status = :confirmedStatus), 0)) >= :seats
            """;

    String SEARCH_ORDER = " ORDER BY t.departureDate ASC, t.id ASC";

    @Query(value = SEARCH_PROJECTION + SEARCH_FROM_WHERE + SEARCH_ORDER,
            countQuery = "SELECT COUNT(t) " + SEARCH_FROM_WHERE)
    Page<TripSearchResult> search(
            @Param("departureCity") String departureCity,
            @Param("arrivalCity") String arrivalCity,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("seats") int seats,
            @Param("publishedStatus") TripStatus publishedStatus,
            @Param("confirmedStatus") ReservationStatus confirmedStatus,
            Pageable pageable);

    @Query(value = SEARCH_PROJECTION + SEARCH_FROM_WHERE + " AND (dropoff.priceFromStart - pickup.priceFromStart) <= :maxPrice" + SEARCH_ORDER,
            countQuery = "SELECT COUNT(t) " + SEARCH_FROM_WHERE + " AND (dropoff.priceFromStart - pickup.priceFromStart) <= :maxPrice")
    Page<TripSearchResult> searchWithMaxPrice(
            @Param("departureCity") String departureCity,
            @Param("arrivalCity") String arrivalCity,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("seats") int seats,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("publishedStatus") TripStatus publishedStatus,
            @Param("confirmedStatus") ReservationStatus confirmedStatus,
            Pageable pageable);

    @Query("SELECT COUNT(t) " + SEARCH_FROM_WHERE)
    long countSearch(
            @Param("departureCity") String departureCity,
            @Param("arrivalCity") String arrivalCity,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("seats") int seats,
            @Param("publishedStatus") TripStatus publishedStatus,
            @Param("confirmedStatus") ReservationStatus confirmedStatus);

    @Query("SELECT COUNT(t) " + SEARCH_FROM_WHERE + " AND (dropoff.priceFromStart - pickup.priceFromStart) <= :maxPrice")
    long countSearchWithMaxPrice(
            @Param("departureCity") String departureCity,
            @Param("arrivalCity") String arrivalCity,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("seats") int seats,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("publishedStatus") TripStatus publishedStatus,
            @Param("confirmedStatus") ReservationStatus confirmedStatus);

    @Query("""
            SELECT t FROM Trip t
            JOIN FETCH t.vehicle v
            JOIN FETCH v.driver
            JOIN FETCH t.stops
            WHERE t.id = :id
            """)
    Optional<Trip> findDetailById(@Param("id") Long id);
}
