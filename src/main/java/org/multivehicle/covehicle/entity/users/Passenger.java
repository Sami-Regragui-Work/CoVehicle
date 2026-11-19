package org.multivehicle.covehicle.entity.users;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity @Table(name = "passengers")
@SuperBuilder
@Getter @Setter @NoArgsConstructor
public class Passenger extends User {
}
