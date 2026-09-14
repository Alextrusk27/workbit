package ru.workbit.training.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
@Table(name = "user_feedback", schema = "training")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingUserFeedback {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "question_id", updatable = false)
    private UUID questionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Vote vote;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, updatable = false, columnDefinition = "text[]")
    private List<String> reasons;

    @Column(updatable = false)
    private String comment;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant created = Instant.now();

    public enum Vote {
        UP, DOWN
    }
}
