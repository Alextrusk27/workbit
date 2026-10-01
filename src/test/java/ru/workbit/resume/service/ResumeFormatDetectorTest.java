package ru.workbit.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.workbit.exception.UnprocessableEntityException;
import ru.workbit.resume.model.Resume;

@DisplayName("ResumeFormatDetectorTest")
class ResumeFormatDetectorTest {
    private static final String UNSUPPORTED = "Unsupported format";
    private static final String LEGACY = "Legacy format";
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final byte[] ZIP_PREFIX = {'P', 'K', 3, 4};
    private static final byte[] OLE2_PREFIX = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] PNG_PREFIX = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final String CYRILLIC_TEXT = "Иванов Иван, разработчик Java. Опыт: Spring, PostgreSQL.\n";
    private static final String PDF_NAME = "resume.pdf";

    private final ResumeFormatDetector detector = new ResumeFormatDetector();

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static byte[] pdfAt(int offset) {
        return concat(ascii(" ".repeat(offset)), ascii("%PDF-1.4\n%body"));
    }

    private static byte[] textWithControls(int totalChars, int controlChars) {
        return ascii("a".repeat(totalChars - controlChars) + "\u0001".repeat(controlChars));
    }

    @Nested
    @DisplayName("Pdf")
    class Pdf {

        @Test
        @DisplayName("Определяет PDF по сигнатуре в нулевом байте")
        void detectsSignatureAtStart() {
            // when
            Resume.Format format = detector.detect(ascii("%PDF-1.7\n1 0 obj"), PDF_NAME);

            // then
            assertThat(format).isEqualTo(Resume.Format.PDF);
        }

        @Test
        @DisplayName("Определяет PDF, когда сигнатура сдвинута внутри первых 1024 байт")
        void detectsSignatureWithinWindow() {
            // when
            Resume.Format format = detector.detect(pdfAt(5), PDF_NAME);

            // then
            assertThat(format).isEqualTo(Resume.Format.PDF);
        }

        @Test
        @DisplayName("Определяет PDF, когда сигнатура заканчивается ровно на последнем байте окна")
        void detectsSignatureEndingAtWindowEdge() {
            // when
            Resume.Format format = detector.detect(pdfAt(1019), PDF_NAME);

            // then
            assertThat(format).isEqualTo(Resume.Format.PDF);
        }

        @Test
        @DisplayName("Не считает PDF файл, где сигнатура начинается после 1024 байт")
        void ignoresSignatureBeyondWindow() {
            // when
            Resume.Format format = detector.detect(pdfAt(1024), PDF_NAME);

            // then
            assertThat(format).isEqualTo(Resume.Format.TXT);
        }

        @Test
        @DisplayName("Не считает PDF файл, где сигнатура пересекает границу окна")
        void ignoresSignatureCrossingWindowEdge() {
            // when
            Resume.Format format = detector.detect(pdfAt(1020), PDF_NAME);

            // then
            assertThat(format).isEqualTo(Resume.Format.TXT);
        }

        @Test
        @DisplayName("Считает PDF файл под паролем: сигнатура и /Encrypt")
        void detectsEncryptedPdf() {
            // given
            byte[] content = ascii("%PDF-1.7\n1 0 obj\n<< /Filter /Standard /Encrypt 5 0 R >>\nendobj\n");

            // when
            Resume.Format format = detector.detect(content, PDF_NAME);

            // then
            assertThat(format).isEqualTo(Resume.Format.PDF);
        }

        @Test
        @DisplayName("Определяет PDF независимо от имени файла")
        void ignoresFilename() {
            // when
            Resume.Format format = detector.detect(ascii("%PDF-1.7 body"), "resume.docx");

            // then
            assertThat(format).isEqualTo(Resume.Format.PDF);
        }
    }

    @Nested
    @DisplayName("Docx")
    class Docx {

        @ParameterizedTest
        @ValueSource(strings = {"resume.docx", "resume.DOCX", "Resume.DocX", "Иванов И.И. резюме.docx", ".docx"})
        @DisplayName("Определяет DOCX для ZIP с расширением .docx в любом регистре")
        void detectsZipWithDocxExtension(String filename) {
            // when
            Resume.Format format = detector.detect(concat(ZIP_PREFIX, ascii("word/document.xml")), filename);

            // then
            assertThat(format).isEqualTo(Resume.Format.DOCX);
        }

        @ParameterizedTest
        @ValueSource(strings = {"table.xlsx", "resume", "", "docx", "x", "resume.docx.zip", "resume.doc", "resume.pdf"})
        @DisplayName("Бросает Unsupported format для ZIP без расширения .docx")
        void throwsForZipWithOtherName(String filename) {
            // given
            byte[] content = concat(ZIP_PREFIX, ascii("xl/workbook.xml"));

            // when / then
            assertThatThrownBy(() -> detector.detect(content, filename))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage(UNSUPPORTED);
        }
    }

    @Nested
    @DisplayName("Legacy")
    class Legacy {

        @ParameterizedTest
        @ValueSource(strings = {"resume.doc", "resume.docx", "resume"})
        @DisplayName("Бросает Legacy format для OLE2 (старый .doc и зашифрованный DOCX имеют одну сигнатуру)")
        void throwsForOle2(String filename) {
            // given
            byte[] content = concat(OLE2_PREFIX, new byte[] {0, 0, 0, 0});

            // when / then
            assertThatThrownBy(() -> detector.detect(content, filename))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage(LEGACY);
        }

        @Test
        @DisplayName("Бросает Legacy format для RTF")
        void throwsForRtf() {
            // given
            byte[] content = ascii("{\\rtf1\\ansi\\deff0 {\\fonttbl {\\f0 Arial;}} Text}");

            // when / then
            assertThatThrownBy(() -> detector.detect(content, "resume.rtf"))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage(LEGACY);
        }
    }

    @Nested
    @DisplayName("Txt")
    class Txt {

        @Test
        @DisplayName("Определяет TXT в UTF-8 с кириллицей")
        void detectsUtf8() {
            // when
            Resume.Format format = detector.detect(CYRILLIC_TEXT.getBytes(StandardCharsets.UTF_8), "resume.txt");

            // then
            assertThat(format).isEqualTo(Resume.Format.TXT);
        }

        @Test
        @DisplayName("Определяет TXT в UTF-8 с BOM")
        void detectsUtf8WithBom() {
            // given
            byte[] content = concat(UTF8_BOM, CYRILLIC_TEXT.getBytes(StandardCharsets.UTF_8));

            // when
            Resume.Format format = detector.detect(content, "resume.txt");

            // then
            assertThat(format).isEqualTo(Resume.Format.TXT);
        }

        @Test
        @DisplayName("Определяет TXT в Windows-1251 с кириллицей")
        void detectsWindows1251() {
            // when
            Resume.Format format = detector.detect(CYRILLIC_TEXT.getBytes(WINDOWS_1251), "resume.txt");

            // then
            assertThat(format).isEqualTo(Resume.Format.TXT);
        }

        @Test
        @DisplayName("Определяет TXT без расширения и при чужом расширении в имени")
        void detectsTextRegardlessOfFilename() {
            // given
            byte[] content = CYRILLIC_TEXT.getBytes(StandardCharsets.UTF_8);

            // when / then
            assertThat(detector.detect(content, "")).isEqualTo(Resume.Format.TXT);
            assertThat(detector.detect(content, "resume.docx")).isEqualTo(Resume.Format.TXT);
        }

        @Test
        @DisplayName("Не считает управляющими символами табы, переводы строк и перевод страницы")
        void allowsWhitespaceControls() {
            // given
            byte[] content = ascii("\t\n\r\f".repeat(50));

            // when
            Resume.Format format = detector.detect(content, "resume.txt");

            // then
            assertThat(format).isEqualTo(Resume.Format.TXT);
        }

        @Test
        @DisplayName("Принимает текст ровно на границе 1% управляющих символов")
        void acceptsExactlyOnePercentControls() {
            // when
            Resume.Format format = detector.detect(textWithControls(200, 2), "resume.txt");

            // then
            assertThat(format).isEqualTo(Resume.Format.TXT);
        }

        @Test
        @DisplayName("Бросает Unsupported format, когда управляющих символов чуть больше 1%")
        void rejectsJustOverOnePercentControls() {
            // given
            byte[] content = textWithControls(199, 2);

            // when / then
            assertThatThrownBy(() -> detector.detect(content, "resume.txt"))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage(UNSUPPORTED);
        }

        @Test
        @DisplayName("Бросает Unsupported format для текста, где много управляющих символов")
        void rejectsManyControls() {
            // given
            byte[] content = textWithControls(100, 30);

            // when / then
            assertThatThrownBy(() -> detector.detect(content, "resume.txt"))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage(UNSUPPORTED);
        }

        @Test
        @DisplayName("Считает управляющими C1-символы в UTF-8")
        void countsC1ControlsInUtf8() {
            // given
            byte[] content = ("\u0085\u0086\u0087" + "а".repeat(10)).getBytes(StandardCharsets.UTF_8);

            // when / then
            assertThatThrownBy(() -> detector.detect(content, "resume.txt"))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage(UNSUPPORTED);
        }
    }

    @Nested
    @DisplayName("TextCharset")
    class TextCharset {

        @Test
        @DisplayName("Возвращает UTF-8 для UTF-8 с кириллицей")
        void returnsUtf8ForCyrillic() {
            // when
            Charset charset = detector.textCharset(CYRILLIC_TEXT.getBytes(StandardCharsets.UTF_8));

            // then
            assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("Возвращает UTF-8 для UTF-8 с BOM")
        void returnsUtf8ForBom() {
            // given
            byte[] content = concat(UTF8_BOM, CYRILLIC_TEXT.getBytes(StandardCharsets.UTF_8));

            // when
            Charset charset = detector.textCharset(content);

            // then
            assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("Возвращает UTF-8 для чистого ASCII")
        void returnsUtf8ForAscii() {
            // when
            Charset charset = detector.textCharset(ascii("Java developer, Spring, PostgreSQL\n"));

            // then
            assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("Возвращает windows-1251 для байтов Windows-1251 с кириллицей")
        void returnsWindows1251ForCyrillic() {
            // when
            Charset charset = detector.textCharset(CYRILLIC_TEXT.getBytes(WINDOWS_1251));

            // then
            assertThat(charset).isEqualTo(WINDOWS_1251);
        }
    }

    @Nested
    @DisplayName("Unsupported")
    class Unsupported {

        @Test
        @DisplayName("Бросает Unsupported format для бинарника с PNG-сигнатурой и NUL-байтами")
        void rejectsPng() {
            // given
            byte[] content = concat(PNG_PREFIX, new byte[] {0, 0, 0, 13}, ascii("IHDR"), new byte[] {0, 0, 1, 0});

            // when / then
            assertThatThrownBy(() -> detector.detect(content, "photo.png"))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage(UNSUPPORTED);
        }

        @Test
        @DisplayName("Бросает Unsupported format, когда в обычном тексте есть NUL-байт")
        void rejectsNulInText() {
            // given
            byte[] content = concat(ascii("обычный "), "текст".getBytes(StandardCharsets.UTF_8), new byte[] {0},
                    ascii(" ещё текст ".repeat(100)));

            // when / then
            assertThatThrownBy(() -> detector.detect(content, "resume.txt"))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage(UNSUPPORTED);
        }

        @Test
        @DisplayName("Бросает Unsupported format для пустого файла")
        void rejectsEmpty() {
            // when / then
            assertThatThrownBy(() -> detector.detect(new byte[0], "resume.txt"))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage(UNSUPPORTED);
        }
    }
}
