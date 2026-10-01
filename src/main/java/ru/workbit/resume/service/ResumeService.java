package ru.workbit.resume.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import ru.workbit.resume.dto.ResumeResponse;
import ru.workbit.resume.model.Resume;
import ru.workbit.resume.model.mapper.ResumeMapper;
import ru.workbit.resume.repository.ResumeRepository;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeService {
    private final ResumeRepository resumeRepository;
    private final ResumeWriter writer;
    private final ResumeFileStorage storage;
    private final ResumeFormatDetector formatDetector;

    private final ResumeMapper resumeMapper;

    public List<ResumeResponse> list(UUID userId) {
        return resumeRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(resumeMapper::toResponse)
                .toList();
    }

    public ResumeResponse upload(UUID userId, @Nullable String filename, byte[] content) {
        String strippedFilename = ResumeName.stripPath(filename);
        Resume.Format format = formatDetector.detect(content, strippedFilename);
        String originalFilename = ResumeName.originalFilename(strippedFilename, format);

        Resume resume = Resume.builder()
                .userId(userId)
                .name(ResumeName.title(strippedFilename))
                .originalFilename(originalFilename)
                .format(format)
                .sizeBytes(content.length)
                .build();
        storage.write(userId, resume.getId(), content);
        Resume saved = saveOrDeleteFile(resume);

        log.info("Resume uploaded uid={} format={} sizeBytes={}", userId, format, content.length);
        return resumeMapper.toResponse(saved);
    }

    private Resume saveOrDeleteFile(Resume resume) {
        try {
            return writer.save(resume);
        } catch (RuntimeException e) {
            try {
                storage.delete(resume.getUserId(), resume.getId());
            } catch (RuntimeException deleteError) {
                e.addSuppressed(deleteError);
            }
            throw e;
        }
    }
}
