package ru.workbit.resume.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.resume")
public record ResumeProperties(
        Path storageDir
) {
}
