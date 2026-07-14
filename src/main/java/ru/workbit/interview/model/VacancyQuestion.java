package ru.workbit.interview.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vacancy_question", schema = "interview")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacancyQuestion {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vacancy_session_id", nullable = false, updatable = false)
    private VacancySession vacancySession;

    @OneToOne(mappedBy = "question", cascade = CascadeType.ALL)
    private VacancyFeedback feedback;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Category category;

    @Column(nullable = false, updatable = false)
    private String questionText;

    @Column(nullable = false, updatable = false)
    private int orderIndex;

    @Builder.Default
    private boolean answered = false;

    @Column
    private String answerText;

    @Column
    private Instant answeredAt;
}
