package ru.workbit.resume.dto;

import java.nio.charset.Charset;
import org.jspecify.annotations.Nullable;
import ru.workbit.resume.model.Resume;

public record ResumeFile(String filename, Resume.Format format, @Nullable Charset charset, byte[] content) {
}
