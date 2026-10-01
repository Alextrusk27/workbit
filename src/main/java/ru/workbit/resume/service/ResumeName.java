package ru.workbit.resume.service;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;
import ru.workbit.resume.model.Resume;

final class ResumeName {
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final String DEFAULT_NAME = "Резюме";
    private static final String DEFAULT_FILENAME_BASE = "resume.";

    private ResumeName() {
    }

    static String stripPath(@Nullable String rawFilename) {
        if (rawFilename == null) {
            return "";
        }
        int separator = Math.max(rawFilename.lastIndexOf('/'), rawFilename.lastIndexOf('\\'));
        return rawFilename.substring(separator + 1).strip();
    }

    static String originalFilename(String filename, Resume.Format format) {
        String truncated = truncate(filename, MAX_FILENAME_LENGTH).strip();
        return truncated.isEmpty()
                ? DEFAULT_FILENAME_BASE + format.name().toLowerCase(Locale.ROOT)
                : truncated;
    }

    static String title(String originalFilename) {
        String base = StringUtils.stripFilenameExtension(originalFilename);
        String title = truncate(base.strip().replaceAll("\\s+", " "), MAX_NAME_LENGTH).strip();
        return title.isEmpty() ? DEFAULT_NAME : title;
    }

    private static String truncate(String value, int maxCodePoints) {
        if (value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }
}
