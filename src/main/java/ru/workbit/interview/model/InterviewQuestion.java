package ru.workbit.interview.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "question", schema = "interview")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestion {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private InterviewSession session;

    @Column(name = "parent_question_id", updatable = false)
    private UUID parentQuestionId;

    @OneToOne(mappedBy = "question", cascade = CascadeType.ALL)
    private InterviewFeedback feedback;

    @Column(nullable = false, updatable = false)
    private String text;

    @Column(nullable = false, updatable = false)
    private int orderIndex;

    @Builder.Default
    @Column(name = "follow_up", nullable = false, updatable = false)
    private boolean followUp = false;

    @Builder.Default
    @Column(name = "follow_up_checked", nullable = false)
    private boolean followUpChecked = false;

    @Builder.Default
    private boolean answered = false;

    @Column
    private String answerText;

    @Column
    private Instant answeredAt;
}
