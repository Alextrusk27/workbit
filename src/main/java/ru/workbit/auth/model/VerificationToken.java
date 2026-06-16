package ru.workbit.auth.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import ru.workbit.user.model.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_token", schema = "auth")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationToken {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Builder.Default
    @Column(nullable = false)
    private Instant expiresAt = Instant.now().plusSeconds(7200);

    private Instant usedAt;

    @Builder.Default
    @Column(nullable = false)
    private Instant created = Instant.now();

    public enum Type {
        PASSWORD_RESET,
        EMAIL_VERIFICATION
    }
}
