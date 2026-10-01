package ru.workbit.resume.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.exception.NotFoundException;
import ru.workbit.resume.ResumeDeletedEvent;
import ru.workbit.resume.model.Resume;
import ru.workbit.resume.repository.ResumeRepository;

@Component
@RequiredArgsConstructor
class ResumeWriter {
    private final ResumeRepository resumeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Resume save(Resume resume) {
        resumeRepository.lockUser(resume.getUserId());
        ResumeService.checkLimit(resumeRepository.countByUserId(resume.getUserId()));
        return resumeRepository.save(resume);
    }

    @Transactional
    public void delete(UUID userId, UUID resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new NotFoundException("Resume not found"));
        resumeRepository.delete(resume);
        eventPublisher.publishEvent(new ResumeDeletedEvent(userId, resumeId));
    }
}
