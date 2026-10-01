package ru.workbit.resume.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import ru.workbit.exception.ConflictException;
import ru.workbit.resume.dto.ResumeResponse;
import ru.workbit.resume.model.Resume;
import ru.workbit.resume.model.mapper.ResumeMapper;
import ru.workbit.resume.repository.ResumeRepository;
import ru.workbit.security.config.RateLimitProperties;
import ru.workbit.security.service.RateLimiterService;
import ru.workbit.util.SingleFlight;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeService {
    private static final int MAX_RESUMES_PER_USER = 3;
    private static final String RESUME_LIMIT_REACHED = "Resume limit reached";

    private final ResumeRepository resumeRepository;
    private final ResumeWriter writer;
    private final ResumeFileStorage storage;
    private final ResumeFormatDetector formatDetector;
    private final RateLimiterService rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final SingleFlight singleFlight;

    private final ResumeMapper resumeMapper;

    public List<ResumeResponse> list(UUID userId) {
        return resumeRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(resumeMapper::toResponse)
                .toList();
    }

    public ResumeResponse upload(UUID userId, @Nullable String filename, byte[] content) {
        return singleFlight.run(
                new SingleFlight.Key("resume.upload", List.of(userId, sha256(content))),
                () -> doUpload(userId, filename, content));
    }

    static void checkLimit(long resumeCount) {
        if (resumeCount >= MAX_RESUMES_PER_USER) {
            throw new ConflictException(RESUME_LIMIT_REACHED);
        }
    }

    private ResumeResponse doUpload(UUID userId, @Nullable String filename, byte[] content) {
        checkLimit(resumeRepository.countByUserId(userId));
        String strippedFilename = ResumeName.stripPath(filename);
        Resume.Format format = formatDetector.detect(content, strippedFilename);
        String originalFilename = ResumeName.originalFilename(strippedFilename, format);
        rateLimiter.check("resume-upload:" + userId, rateLimitProperties.resumeUpload());

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

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
