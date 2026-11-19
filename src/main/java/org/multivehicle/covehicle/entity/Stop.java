package org.multivehicle.covehicle.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stops", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"trip_id", "city"}),
        @UniqueConstraint(columnNames = {"trip_id", "stop_order"})
})
@Builder @AllArgsConstructor
@Getter @Setter @NoArgsConstructor
public class Stop {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private int stopOrder;

    @Column(nullable = false)
    private LocalDateTime arrivalTime;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceFromStart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Trip trip;
}
