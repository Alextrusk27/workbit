package ru.workbit.resume.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.resume.model.Resume;
import ru.workbit.resume.repository.ResumeRepository;

@Component
@RequiredArgsConstructor
class ResumeWriter {
    private final ResumeRepository resumeRepository;

    @Transactional
    public Resume save(Resume resume) {
        resumeRepository.lockUser(resume.getUserId());
        ResumeService.checkLimit(resumeRepository.countByUserId(resume.getUserId()));
        return resumeRepository.save(resume);
    }
}
