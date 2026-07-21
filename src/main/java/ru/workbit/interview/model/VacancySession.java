package ru.workbit.interview.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "vacancy_session", schema = "interview")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacancySession {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "vacancy_snapshot_id", nullable = false)
    private UUID vacancySnapshotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.CREATED;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant created = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "vacancySession", orphanRemoval = true, cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<VacancyQuestion> questions;

    @OneToOne(mappedBy = "vacancySession", orphanRemoval = true, cascade = CascadeType.ALL)
    private VacancyReport report;

    public enum Status {
        CREATED, IN_PROGRESS, COMPLETED
    }
}
