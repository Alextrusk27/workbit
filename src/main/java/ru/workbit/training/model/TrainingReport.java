package ru.workbit.training.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "report", schema = "training")
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingReport {
    @Id
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
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
