package ru.workbit.interview.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
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

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant generatedAt = Instant.now();
}
