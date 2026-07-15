package ru.workbit.interview.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "training_question", schema = "interview")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingQuestion {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_session_id", nullable = false, updatable = false)
    private TrainingSession trainingSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_question_id", updatable = false)
    private TrainingQuestion parentQuestion;

    @Column(name = "bank_question_id", updatable = false)
    private UUID bankQuestionId;

    @OneToOne(mappedBy = "question", cascade = CascadeType.ALL)
    private TrainingFeedback feedback;

    @Column(nullable = false, updatable = false)
    private String questionText;

    @Column(nullable = false, updatable = false)
    private int orderIndex;

    @Builder.Default
    @Column(name = "follow_up", nullable = false, updatable = false)
    private boolean followUp = false;

    @Builder.Default
    private boolean answered = false;

    @Column
    private String answerText;

    @Column
    private Instant answeredAt;
}
