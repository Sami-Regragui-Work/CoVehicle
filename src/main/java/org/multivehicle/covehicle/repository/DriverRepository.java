package org.multivehicle.covehicle.repository;

import org.multivehicle.covehicle.entity.users.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    boolean existsByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumberAndIdNot(String licenseNumber, Long id);

//    List<Driver> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
//            String firstName, String lastName, String email);

    @Query("""
        SELECT d FROM Driver d
        WHERE LOWER(d.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(d.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(d.email)     LIKE LOWER(CONCAT('%', :q, '%'))
        """)
    List<Driver> search(@Param("q") String q);
}
