package org.multivehicle.covehicle.entity.users;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@SuperBuilder
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;
    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    @Builder.Default
    @Column(columnDefinition = "boolean default true")
    private boolean isActive = true;
}