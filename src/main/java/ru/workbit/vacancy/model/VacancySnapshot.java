package ru.workbit.vacancy.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "snapshot", schema = "vacancy")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacancySnapshot {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "hh_vacancy_id")
    private Long hhVacancyId;

    private String url;

    @Column(nullable = false)
    private String name;

    private String employer;

    private String experience;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "key_skills", columnDefinition = "text[]")
    private List<String> keySkills;

    @Column(nullable = false)
    private String description;

    @Builder.Default
    @Column(name = "fetched_at", nullable = false, updatable = false)
    private Instant fetchedAt = Instant.now();
}
