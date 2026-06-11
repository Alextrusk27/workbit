package ru.workbit.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class User {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    private String lastName;

    private String firstName;

    private String middleName;

    @Column(name = "pwd_hash", nullable = false)
    @ToString.Exclude
    private String password;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant created = Instant.now();

    private Instant deactivated;
}
