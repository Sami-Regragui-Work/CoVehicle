package org.multivehicle.covehicle.entity;

import jakarta.persistence.*;
import lombok.*;
import org.multivehicle.covehicle.entity.enums.TripStatus;
import org.multivehicle.covehicle.entity.users.Driver;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "trips")
@Builder @AllArgsConstructor
@Getter @Setter @NoArgsConstructor
public class Trip {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime departureDate;

    @Column(nullable = false)
    private int seatsOffered;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TripStatus status = TripStatus.PUBLISHED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Vehicle vehicle;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stopOrder ASC")
    @Builder.Default
    private List<Stop> stops = new ArrayList<>();

    public void addStop(Stop stop) {
        stop.setTrip(this);
        stops.add(stop);
    }

    public String getDepartureCity() { return stops.getFirst().getCity(); }
    public String getArrivalCity()   { return stops.getLast().getCity(); }
    public Driver getDriver()        { return vehicle.getDriver(); }

    @PrePersist @PreUpdate
    private void syncDepartureDate() {
        if (!stops.isEmpty()) {
            this.departureDate = stops.getFirst().getArrivalTime();
        }
    }
}
