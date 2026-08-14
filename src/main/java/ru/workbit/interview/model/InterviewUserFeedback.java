package ru.workbit.interview.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_feedback", schema = "interview")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewUserFeedback {
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
