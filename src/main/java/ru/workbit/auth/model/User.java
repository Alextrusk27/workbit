package ru.workbit.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "users", schema = "auth")
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

    @Column(nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant created = Instant.now();

    @Column(name = "last_seen", nullable = false)
    @Builder.Default
    private Instant lastSeen = Instant.now();

    @Column(name = "deletion_warned_at")
    private Instant deletionWarnedAt;

    @Column(name = "personal_data_consent_at")
    private Instant personalDataConsentAt;
}
