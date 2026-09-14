package ru.workbit.interview.model;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

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
    }
}
