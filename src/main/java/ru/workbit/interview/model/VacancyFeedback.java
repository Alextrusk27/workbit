package ru.workbit.interview.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vacancy_feedback", schema = "interview")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacancyFeedback {
    @Id
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private VacancyQuestion question;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private String feedbackText;

    @Builder.Default
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt = Instant.now();
}
