package ru.workbit.content.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "question_bank", schema = "content")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankQuestion {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "profession_id", nullable = false, updatable = false)
    private UUID professionId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "varchar(32)[]")
    private List<String> levels;

    @Column(nullable = false, updatable = false)
    private String text;

    @Column(name = "reference_answer", updatable = false)
    private String referenceAnswer;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private String source = "GENERATED";

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant created = Instant.now();
}
