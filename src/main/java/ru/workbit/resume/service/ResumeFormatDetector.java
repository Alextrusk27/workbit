package ru.workbit.resume.service;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import ru.workbit.exception.UnprocessableEntityException;
import ru.workbit.resume.model.Resume;

@Component
public class ResumeFormatDetector {
    private static final String UNSUPPORTED_FORMAT = "Unsupported format";
    private static final String PDF_SIGNATURE = "%PDF-";
    private static final int PDF_SIGNATURE_WINDOW = 1024;

    public Resume.Format detect(byte[] content) {
        if (head(content).contains(PDF_SIGNATURE)) {
            return Resume.Format.PDF;
        }
        throw new UnprocessableEntityException(UNSUPPORTED_FORMAT);
    }

    private static String head(byte[] content) {
        int length = Math.min(content.length, PDF_SIGNATURE_WINDOW);
        return new String(content, 0, length, StandardCharsets.ISO_8859_1);
    }
}
