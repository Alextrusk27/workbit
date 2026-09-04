package ru.workbit.interview.model;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "session", schema = "interview")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {
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

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "plan_topics", columnDefinition = "text[]")
    private List<String> planTopics;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant created = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "closing_remark")
    private String closingRemark;

    @OneToMany(mappedBy = "session", orphanRemoval = true, cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<InterviewQuestion> questions;

    @OneToOne(mappedBy = "session", orphanRemoval = true, cascade = CascadeType.ALL)
    private InterviewReport report;

    public enum Status {
        CREATED, IN_PROGRESS, COMPLETED
    }
}
