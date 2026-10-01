package ru.workbit.resume.controller;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.workbit.resume.dto.ResumeFile;
import ru.workbit.resume.dto.ResumeResponse;
import ru.workbit.resume.model.Resume;
import ru.workbit.resume.service.ResumeService;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.util.annotation.Loggable;

@RestController
@RequiredArgsConstructor
public class ResumeController implements ResumeApi {
    private final ResumeService resumeService;

    @Override
    @Loggable(level = "DEBUG")
    public ResponseEntity<List<ResumeResponse>> list(CustomUserDetails userDetails) {
        return ResponseEntity.ok(resumeService.list(userDetails.getId()));
    }

    @Override
    @Loggable
    public ResponseEntity<ResumeResponse> upload(MultipartFile file, CustomUserDetails userDetails)
            throws IOException {
        var resume = resumeService.upload(userDetails.getId(), file.getOriginalFilename(), file.getBytes());
        return ResponseEntity
                .created(URI.create("/api/v1/resumes/" + resume.id()))
                .body(resume);
    }

    @Override
    @Loggable(level = "DEBUG")
    public ResponseEntity<Resource> file(UUID id, CustomUserDetails userDetails) {
        ResumeFile file = resumeService.file(userDetails.getId(), id);
        ContentDisposition.Builder disposition = file.format() == Resume.Format.DOCX
                ? ContentDisposition.attachment()
                : ContentDisposition.inline();
        return ResponseEntity.ok()
                .contentType(mediaType(file))
                .headers(headers -> headers.setContentDisposition(
                        disposition.filename(file.filename(), StandardCharsets.UTF_8).build()))
                .header("Content-Security-Policy", "sandbox")
                .body(new ByteArrayResource(file.content()));
    }

    @Override
    @Loggable
    public ResponseEntity<Void> delete(UUID id, CustomUserDetails userDetails) {
        resumeService.delete(userDetails.getId(), id);
        return ResponseEntity.noContent().build();
    }

    private static MediaType mediaType(ResumeFile file) {
        return switch (file.format()) {
            case PDF -> MediaType.APPLICATION_PDF;
            case DOCX -> MediaType.parseMediaType(DOCX_MEDIA_TYPE);
            case TXT -> new MediaType(MediaType.TEXT_PLAIN, Objects.requireNonNull(file.charset()));
        };
    }
}
