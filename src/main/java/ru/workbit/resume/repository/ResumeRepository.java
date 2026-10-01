package ru.workbit.resume.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.resume.model.Resume;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Resume> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(CAST(:userId AS text), 0))",
            nativeQuery = true)
    void lockUser(UUID userId);
}
