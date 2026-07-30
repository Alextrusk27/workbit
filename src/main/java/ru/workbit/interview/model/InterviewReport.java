package ru.workbit.interview.model;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "report", schema = "interview")
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewReport {
    @Id
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private InterviewSession session;

    @Column(nullable = false, updatable = false)
    private Double avgScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OfferProbability offerProbability;

    @Column(nullable = false, updatable = false)
    @Size(min = 10)
    private String overallFeedback;

    @Column(updatable = false)
    private String recommendations;

    @Column(updatable = false)
    @Size(max = 100)
    private String weakestSkill;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant generatedAt = Instant.now();

    @Getter
    public enum OfferProbability {
        LOW("Низкая"),
        MEDIUM("Средняя"),
        HIGH("Высокая");

        @JsonValue
        private final String name;

        OfferProbability(String name) {
            this.name = name;
        }

        public static Optional<OfferProbability> fromString(String value) {
            if (value == null) {
                return Optional.empty();
            }
            String normalized = value.trim();
            for (OfferProbability probability : values()) {
                if (probability.name().equalsIgnoreCase(normalized)
                        || probability.name.equalsIgnoreCase(normalized)) {
                    return Optional.of(probability);
                }
            }
            return Optional.empty();
        }
    }
}
