package ru.workbit.training.model;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "training_session", schema = "training")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSession {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String profession;

    @Column(length = 100)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Level level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.CREATED;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant created = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "trainingSession", orphanRemoval = true, cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TrainingQuestion> questions;

    @OneToOne(mappedBy = "trainingSession", orphanRemoval = true, cascade = CascadeType.ALL)
    private TrainingReport report;

    public enum Status {
        CREATED, IN_PROGRESS, COMPLETED
    }

    @Getter
    public enum Level {
        JUNIOR("Junior", "Начинающий"),
        MIDDLE("Middle", "Уверенный"),
        SENIOR("Senior", "Продвинутый");

        private final String grade;

        @JsonValue
        private final String label;

        Level(String grade, String label) {
            this.grade = grade;
            this.label = label;
        }
    }
}
