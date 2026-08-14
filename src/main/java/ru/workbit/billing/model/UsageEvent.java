package ru.workbit.billing.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usage_event", schema = "billing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageEvent {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant at = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Target target;

    @Column(nullable = false, updatable = false)
    private int delta;

    @Column(nullable = false, updatable = false)
    private String label;

    public enum Kind {
        SPEND, CREDIT
    }

    public enum Target {
        INTERVIEW, TRAINING
    }
}
