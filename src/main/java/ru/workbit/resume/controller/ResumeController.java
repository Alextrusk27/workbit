package ru.workbit.resume.controller;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.workbit.resume.dto.ResumeResponse;
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
    @Loggable
    public ResponseEntity<Void> delete(UUID id, CustomUserDetails userDetails) {
        resumeService.delete(userDetails.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
