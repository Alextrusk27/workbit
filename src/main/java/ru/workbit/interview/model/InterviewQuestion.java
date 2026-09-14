package ru.workbit.interview.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

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

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Kind kind = Kind.MAIN;

    @Column(updatable = false)
    private String topic;

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

    public enum Kind {
        MAIN, FOLLOW_UP, CLARIFICATION, REDIRECT
    }
}
