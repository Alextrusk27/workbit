package ru.workbit.training.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "training_report", schema = "training")
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingReport {
    @Id
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "training_session_id", nullable = false, updatable = false)
    private TrainingSession trainingSession;

    @Column(nullable = false, updatable = false)
    private Double avgScore;

    @Column(nullable = false, updatable = false)
    @Size(min = 10)
    private String overallFeedback;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant generatedAt = Instant.now();
}
