package ru.workbit.content.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profession_dict", schema = "content")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionDict {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DictStatus status = DictStatus.AUTO;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private int usageCount = 0;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant created = Instant.now();
}
