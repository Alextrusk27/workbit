package ru.workbit.resume;

import java.util.UUID;

public record ResumeDeletedEvent(UUID userId, UUID resumeId) {
}
