package ru.workbit.resume.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.workbit.resume.model.Resume;

@DisplayName("ResumeNameTest")
class ResumeNameTest {
    private static final String DEFAULT_TITLE = "Резюме";
    private static final String EMOJI = "😀";

    private static int codePoints(String value) {
        return value.codePointCount(0, value.length());
    }

    @Nested
    @DisplayName("StripPath")
    class StripPath {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "   \t "})
        @DisplayName("Возвращает пустую строку для null, пустого имени и одних пробелов")
        void returnsEmptyForBlank(String raw) {
            // when / then
            assertThat(ResumeName.stripPath(raw)).isEmpty();
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', textBlock = """
                resume.pdf                     | resume.pdf
                /home/ivan/resume.pdf          | resume.pdf
                C:\\Users\\ivan\\resume.pdf    | resume.pdf
                docs/old\\2024/resume.docx     | resume.docx
                docs\\old/2024\\resume.txt     | resume.txt
                ../../etc/resume.pdf           | resume.pdf
                /resume.pdf                    | resume.pdf
                """)
        @DisplayName("Отрезает путь с / и \\ и оставляет только имя файла")
        void removesDirectories(String raw, String expected) {
            // when / then
            assertThat(ResumeName.stripPath(raw)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"docs/", "C:\\docs\\", "/", "\\"})
        @DisplayName("Возвращает пустую строку, когда путь заканчивается разделителем")
        void returnsEmptyForTrailingSeparator(String raw) {
            // when / then
            assertThat(ResumeName.stripPath(raw)).isEmpty();
        }

        @Test
        @DisplayName("Убирает пробелы по краям имени и сохраняет пробелы внутри")
        void stripsSurroundingWhitespace() {
            // when / then
            assertThat(ResumeName.stripPath("  dir/ Иванов  Иван.pdf \t")).isEqualTo("Иванов  Иван.pdf");
        }
    }

    @Nested
    @DisplayName("OriginalFilename")
    class OriginalFilename {

        @Test
        @DisplayName("Возвращает имя как есть, когда оно короче лимита")
        void keepsShortName() {
            // when / then
            assertThat(ResumeName.originalFilename("Иванов Java.pdf", Resume.Format.PDF)).isEqualTo("Иванов Java.pdf");
        }

        @Test
        @DisplayName("Не режет имя ровно в 255 code points")
        void keepsNameAtLimit() {
            // given
            String name = "a".repeat(255);

            // when / then
            assertThat(ResumeName.originalFilename(name, Resume.Format.TXT)).isEqualTo(name);
        }

        @Test
        @DisplayName("Обрезает имя длиннее 255 до 255 code points")
        void truncatesLongName() {
            // given
            String name = "a".repeat(300) + ".pdf";

            // when
            String result = ResumeName.originalFilename(name, Resume.Format.PDF);

            // then
            assertThat(result).isEqualTo("a".repeat(255));
        }

        @Test
        @DisplayName("Считает суррогатные пары одним code point и не рвёт их при обрезке")
        void truncatesBySurrogatePairs() {
            // given
            String name = EMOJI.repeat(300);

            // when
            String result = ResumeName.originalFilename(name, Resume.Format.DOCX);

            // then
            assertThat(codePoints(result)).isEqualTo(255);
            assertThat(result).isEqualTo(EMOJI.repeat(255));
            assertThat(result).hasSize(510);
        }

        @Test
        @DisplayName("Не режет имя из 255 суррогатных пар")
        void keepsSurrogatePairsAtLimit() {
            // given
            String name = EMOJI.repeat(255);

            // when / then
            assertThat(ResumeName.originalFilename(name, Resume.Format.TXT)).isEqualTo(name);
        }

        @Test
        @DisplayName("Убирает пробелы на конце после обрезки")
        void stripsAfterTruncation() {
            // given
            String name = "a".repeat(254) + " bbb";

            // when
            String result = ResumeName.originalFilename(name, Resume.Format.PDF);

            // then
            assertThat(result).isEqualTo("a".repeat(254));
        }

        @ParameterizedTest
        @CsvSource({"PDF,resume.pdf", "DOCX,resume.docx", "TXT,resume.txt"})
        @DisplayName("Подставляет resume.<ext> по формату для пустого имени")
        void usesDefaultForEmpty(Resume.Format format, String expected) {
            // when / then
            assertThat(ResumeName.originalFilename("", format)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Подставляет resume.<ext> для имени из одних пробелов")
        void usesDefaultForBlank() {
            // when / then
            assertThat(ResumeName.originalFilename("   \t ", Resume.Format.DOCX)).isEqualTo("resume.docx");
        }
    }

    @Nested
    @DisplayName("Title")
    class Title {

        @ParameterizedTest
        @CsvSource(delimiter = '|', textBlock = """
                cv.pdf                 | cv
                cv.PDF                 | cv
                Resume.DocX            | Resume
                notes.txt              | notes
                Иванов Java.pdf        | Иванов Java
                v2.1 резюме            | v2.1 резюме
                Иванов И.И.            | Иванов И.И.
                cv.doc                 | cv.doc
                cv.pdf.txt             | cv.pdf
                cv.pdf.docx            | cv.pdf
                my.resume.pdf          | my.resume
                pdf                    | pdf
                """)
        @DisplayName("Отрезает только расширения pdf, docx, txt без учёта регистра")
        void stripsKnownExtensionsOnly(String originalFilename, String expected) {
            // when / then
            assertThat(ResumeName.title(originalFilename)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {".pdf", ".DOCX", ".txt", "", "   ", " .pdf", "\t"})
        @DisplayName("Возвращает «Резюме», когда после отрезания расширения ничего не осталось")
        void returnsDefaultWhenEmpty(String originalFilename) {
            // when / then
            assertThat(ResumeName.title(originalFilename)).isEqualTo(DEFAULT_TITLE);
        }

        @Test
        @DisplayName("Схлопывает подряд идущие пробелы и табы в один пробел")
        void collapsesWhitespace() {
            // when / then
            assertThat(ResumeName.title("  Иванов \t\t Иван   Java \t.pdf")).isEqualTo("Иванов Иван Java");
        }

        @Test
        @DisplayName("Убирает пробелы между именем и расширением")
        void stripsSpaceBeforeExtension() {
            // when / then
            assertThat(ResumeName.title("cv .pdf")).isEqualTo("cv");
        }

        @Test
        @DisplayName("Не режет название ровно в 100 code points")
        void keepsTitleAtLimit() {
            // given
            String name = "a".repeat(100);

            // when / then
            assertThat(ResumeName.title(name + ".pdf")).isEqualTo(name);
        }

        @Test
        @DisplayName("Обрезает название длиннее 100 до 100 code points")
        void truncatesLongTitle() {
            // when / then
            assertThat(ResumeName.title("a".repeat(150) + ".pdf")).isEqualTo("a".repeat(100));
        }

        @Test
        @DisplayName("Считает суррогатные пары одним code point и не рвёт их при обрезке")
        void truncatesBySurrogatePairs() {
            // given
            String name = EMOJI.repeat(150);

            // when
            String result = ResumeName.title(name + ".txt");

            // then
            assertThat(codePoints(result)).isEqualTo(100);
            assertThat(result).isEqualTo(EMOJI.repeat(100));
        }

        @Test
        @DisplayName("Убирает пробел на конце, оставшийся после обрезки")
        void stripsAfterTruncation() {
            // given
            String name = "a".repeat(99) + " bbb.docx";

            // when
            String result = ResumeName.title(name);

            // then
            assertThat(result).isEqualTo("a".repeat(99));
        }

        @Test
        @DisplayName("Схлопывает пробелы до обрезки, а не после")
        void collapsesBeforeTruncation() {
            // given
            String name = "a".repeat(98) + "     bb.pdf";

            // when
            String result = ResumeName.title(name);

            // then
            assertThat(result).isEqualTo("a".repeat(98) + " b");
        }
    }

    @Nested
    @DisplayName("HasExtension")
    class HasExtension {

        @ParameterizedTest
        @ValueSource(strings = {"cv.pdf", "cv.PDF", "cv.Pdf", ".pdf", "Иванов.pdf"})
        @DisplayName("Находит расширение .pdf без учёта регистра")
        void matchesPdf(String filename) {
            // when / then
            assertThat(ResumeName.hasExtension(filename, Resume.Format.PDF)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"cv.docx", "cv.DOCX", ".docx"})
        @DisplayName("Находит расширение .docx без учёта регистра")
        void matchesDocx(String filename) {
            // when / then
            assertThat(ResumeName.hasExtension(filename, Resume.Format.DOCX)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"cv.txt", "cv.TXT"})
        @DisplayName("Находит расширение .txt без учёта регистра")
        void matchesTxt(String filename) {
            // when / then
            assertThat(ResumeName.hasExtension(filename, Resume.Format.TXT)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "x", "pdf", "cv_pdf", "cv.pdf ", "cv.pdf.zip", "cv.doc", "cv.docx"})
        @DisplayName("Не находит .pdf в коротких именах, без точки и с другим расширением")
        void doesNotMatchPdf(String filename) {
            // when / then
            assertThat(ResumeName.hasExtension(filename, Resume.Format.PDF)).isFalse();
        }

        @Test
        @DisplayName("Сверяет расширение именно с запрошенным форматом")
        void checksRequestedFormatOnly() {
            // when / then
            assertThat(ResumeName.hasExtension("cv.docx", Resume.Format.TXT)).isFalse();
            assertThat(ResumeName.hasExtension("cv.txt", Resume.Format.DOCX)).isFalse();
        }
    }
}
