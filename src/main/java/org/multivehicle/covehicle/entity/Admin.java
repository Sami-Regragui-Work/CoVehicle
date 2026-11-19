package org.multivehicle.covehicle.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "admins")
@Builder @AllArgsConstructor
@Getter @Setter @NoArgsConstructor
public class Admin {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;
}
