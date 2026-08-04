package ru.workbit.auth.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_code", schema = "auth")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginCode {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String codeHash;

    @Builder.Default
    @Column(nullable = false)
    private Instant expiresAt = Instant.now().plusSeconds(900);

    @Column(nullable = false)
    private int attempts;

    private Instant usedAt;

    @Builder.Default
    @Column(nullable = false)
    private Instant created = Instant.now();
}
