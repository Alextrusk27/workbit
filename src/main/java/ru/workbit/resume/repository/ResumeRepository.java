package ru.workbit.resume.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.resume.model.Resume;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Resume> findByIdAndUserId(UUID id, UUID userId);
}
