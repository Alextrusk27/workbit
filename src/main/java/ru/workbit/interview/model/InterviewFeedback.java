package ru.workbit.interview.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feedback", schema = "interview")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewFeedback {
    @Id
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private InterviewQuestion question;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private String text;

    @Builder.Default
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt = Instant.now();
}
