package ru.workbit.training.model;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "session", schema = "training")
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
    private String skill;

    @Column(nullable = false, length = 100)
    private String profession;

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
        NOEXP("noexp", "Базовый"),
        JUNIOR("junior", "Начинающий"),
        MIDDLE("middle", "Уверенный"),
        SENIOR("senior", "Продвинутый");

        private final String grade;

        @JsonValue
        private final String label;

        Level(String grade, String label) {
            this.grade = grade;
            this.label = label;
        }
    }
}
