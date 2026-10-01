package ru.workbit.resume.service;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import ru.workbit.exception.UnprocessableEntityException;
import ru.workbit.resume.model.Resume;

@Component
public class ResumeFormatDetector {
    private static final String UNSUPPORTED_FORMAT = "Unsupported format";
    private static final String LEGACY_FORMAT = "Legacy format";
    private static final String PDF_SIGNATURE = "%PDF-";
    private static final int PDF_SIGNATURE_WINDOW = 1024;
    private static final byte[] ZIP_SIGNATURE = {'P', 'K', 3, 4};
    private static final byte[] OLE2_SIGNATURE = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };
    private static final byte[] RTF_SIGNATURE = {'{', '\\', 'r', 't', 'f'};
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final int MAX_CONTROL_CHARS_PERCENT = 1;

    public Resume.Format detect(byte[] content, String filename) {
        if (head(content).contains(PDF_SIGNATURE)) {
            return Resume.Format.PDF;
        }
        if (startsWith(content, ZIP_SIGNATURE)) {
            if (ResumeName.hasExtension(filename, Resume.Format.DOCX)) {
                return Resume.Format.DOCX;
            }
            throw new UnprocessableEntityException(UNSUPPORTED_FORMAT);
        }
        if (startsWith(content, OLE2_SIGNATURE) || startsWith(content, RTF_SIGNATURE)) {
            throw new UnprocessableEntityException(LEGACY_FORMAT);
        }
        if (isText(content)) {
            return Resume.Format.TXT;
        }
        throw new UnprocessableEntityException(UNSUPPORTED_FORMAT);
    }

    private static String head(byte[] content) {
        int length = Math.min(content.length, PDF_SIGNATURE_WINDOW);
        return new String(content, 0, length, StandardCharsets.ISO_8859_1);
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length
                && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static boolean isText(byte[] content) {
        if (content.length == 0 || containsNul(content)) {
            return false;
        }
        String text = decode(content);
        long controlChars = text.chars().filter(ResumeFormatDetector::isForbiddenControl).count();
        return controlChars * 100 <= (long) text.length() * MAX_CONTROL_CHARS_PERCENT;
    }

    private static boolean containsNul(byte[] content) {
        for (byte b : content) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private static String decode(byte[] content) {
        int offset = startsWith(content, UTF8_BOM) ? UTF8_BOM.length : 0;
        ByteBuffer bytes = ByteBuffer.wrap(content, offset, content.length - offset);
        try {
            CharBuffer chars = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(bytes);
            return chars.toString();
        } catch (CharacterCodingException e) {
            return new String(content, WINDOWS_1251);
        }
    }

    private static boolean isForbiddenControl(int c) {
        return Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r' && c != '\f';
    }
}
