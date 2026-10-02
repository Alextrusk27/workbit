package ru.workbit.resume.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "document", schema = "resume")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resume implements Persistable<@NonNull UUID> {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Setter
    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "original_filename", nullable = false, updatable = false)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private Format format;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean persisted;

    @Builder
    private Resume(UUID userId, String name, String originalFilename, Format format, int sizeBytes) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.originalFilename = originalFilename;
        this.format = format;
        this.sizeBytes = sizeBytes;
        this.createdAt = Instant.now();
    }

    @Override
    public @NonNull UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        persisted = true;
    }

    public enum Format {
        PDF, DOCX, TXT
    }
}
