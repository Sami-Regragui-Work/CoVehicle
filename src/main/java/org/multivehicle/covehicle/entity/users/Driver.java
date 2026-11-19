package org.multivehicle.covehicle.entity.users;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity @Table(name = "drivers")
@SuperBuilder
@Getter @Setter @NoArgsConstructor
public class Driver extends User {
    @Column(nullable = false, unique = true)
    private String licenseNumber;
}
