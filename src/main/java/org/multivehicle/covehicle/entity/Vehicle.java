package org.multivehicle.covehicle.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.multivehicle.covehicle.entity.users.Driver;

@Entity @Table(name = "vehicles")
@Builder @AllArgsConstructor
@Getter @Setter @NoArgsConstructor
public class Vehicle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String brand;
    @Column(nullable = false)
    private String model;
    private String color;

    @Column(nullable = false, unique = true)
    private String licensePlate;

    @Min(1) @Max(8)
    @Column(nullable = false)
    private int capacity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Driver driver;
}
