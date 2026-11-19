package org.multivehicle.covehicle.repository;

import org.multivehicle.covehicle.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    @Query("""
            SELECT t FROM Trip t
            JOIN FETCH t.vehicle v
            JOIN FETCH v.driver
            JOIN FETCH t.stops
            WHERE t.id = :id
            """)
    Optional<Trip> findDetailById(@Param("id") Long id);

}
