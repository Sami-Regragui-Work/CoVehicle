package org.multivehicle.covehicle.repository;

import org.multivehicle.covehicle.entity.users.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PassengerRepository extends JpaRepository<Passenger, Long> {
    Optional<Passenger> findByEmail(String email);

    @Query("""
            SELECT CONCAT(p.firstName, " ", p.lastName) AS fullname FROM Passenger p
            JOIN Reservation r
            ON r.passenger.id = p.id
            GROUP BY fullname
            HAVING COUNT(r.id) >= :nbMin
            ORDER BY fullname ASC
            """)
    List<Passenger> getPassengerActif(
            @Param("nbMin") int nbMin
    );
}
