package ru.workbit.resume.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.workbit.resume.config.ResumeProperties;

@Component
public class ResumeFileStorage {
    private final Path root;

    public ResumeFileStorage(ResumeProperties properties) {
        this.root = properties.storageDir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create resume storage dir " + root, e);
        }
        if (!Files.isWritable(root)) {
            throw new IllegalStateException("Resume storage dir is not writable: " + root);
        }
    }

    public void write(UUID userId, UUID resumeId, byte[] content) {
        Path dir = userDir(userId);
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            tmp = Files.createTempFile(dir, resumeId + "-", ".tmp");
            Files.write(tmp, content);
            Files.move(tmp, dir.resolve(resumeId.toString()), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new UncheckedIOException(e);
        }
    }

    public Optional<byte[]> read(UUID userId, UUID resumeId) {
        try {
            return Optional.of(Files.readAllBytes(userDir(userId).resolve(resumeId.toString())));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(UUID userId, UUID resumeId) {
        try {
            Files.deleteIfExists(userDir(userId).resolve(resumeId.toString()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path userDir(UUID userId) {
        return root.resolve(userId.toString());
    }

    private static void deleteQuietly(@Nullable Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // intentionally ignored: an orphan temp file is harmless
        }
    }
}
