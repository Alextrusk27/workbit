package ru.workbit.interview.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "question_bank", schema = "interview")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankQuestion {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Level level;

    @Column(nullable = false, updatable = false)
    private String text;
}
