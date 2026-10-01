package ru.workbit.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.resume.ResumeDeletedEvent;
import ru.workbit.resume.model.Resume;
import ru.workbit.resume.repository.ResumeRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResumeWriterTest")
class ResumeWriterTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RESUME_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    ResumeRepository resumeRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ResumeWriter writer;

    private static Resume aResume() {
        return Resume.builder()
                .userId(USER_ID)
                .name("Иванов Java")
                .originalFilename("Иванов Java.pdf")
                .format(Resume.Format.PDF)
                .sizeBytes(100)
                .build();
    }

    @Nested
    @DisplayName("Save")
    class Save {

        @Test
        @DisplayName("Берёт блокировку пользователя, считает резюме и только потом сохраняет, возвращает сохранённое")
        void locksCountsThenSaves() {
            // given
            Resume resume = aResume();
            when(resumeRepository.countByUserId(USER_ID)).thenReturn(2L);
            when(resumeRepository.save(resume)).thenReturn(resume);

            // when
            Resume result = writer.save(resume);

            // then
            InOrder order = inOrder(resumeRepository);
            order.verify(resumeRepository).lockUser(USER_ID);
            order.verify(resumeRepository).countByUserId(USER_ID);
            order.verify(resumeRepository).save(resume);
            assertThat(result).isSameAs(resume);
        }

        @ParameterizedTest
        @ValueSource(longs = {3, 4})
        @DisplayName("Бросает ConflictException и не сохраняет, когда резюме уже 3 или больше")
        void throwsWhenLimitReached(long count) {
            // given
            Resume resume = aResume();
            when(resumeRepository.countByUserId(USER_ID)).thenReturn(count);

            // when / then
            assertThatThrownBy(() -> writer.save(resume))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Resume limit reached");
            InOrder order = inOrder(resumeRepository);
            order.verify(resumeRepository).lockUser(USER_ID);
            order.verify(resumeRepository).countByUserId(USER_ID);
            verify(resumeRepository, never()).save(resume);
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("Удаляет найденное резюме и публикует событие с userId и resumeId")
        void deletesAndPublishesEvent() {
            // given
            Resume resume = aResume();
            when(resumeRepository.findByIdAndUserId(RESUME_ID, USER_ID)).thenReturn(Optional.of(resume));

            // when
            writer.delete(USER_ID, RESUME_ID);

            // then
            InOrder order = inOrder(resumeRepository, eventPublisher);
            order.verify(resumeRepository).delete(resume);
            order.verify(eventPublisher).publishEvent(new ResumeDeletedEvent(USER_ID, RESUME_ID));
        }

        @Test
        @DisplayName("Бросает NotFoundException без удаления и события, когда резюме нет у этого пользователя")
        void throwsWhenNotFound() {
            // given
            when(resumeRepository.findByIdAndUserId(RESUME_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> writer.delete(OTHER_USER_ID, RESUME_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Resume not found");
            verify(resumeRepository).findByIdAndUserId(RESUME_ID, OTHER_USER_ID);
            verifyNoMoreInteractions(resumeRepository);
            verifyNoInteractions(eventPublisher);
        }
    }
}
