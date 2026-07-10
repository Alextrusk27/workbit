package ru.workbit.vacancy.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.exception.NotFoundException;
import ru.workbit.vacancy.client.HhClient;
import ru.workbit.vacancy.dto.HhVacancyResponse;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.dto.VacancyPreviewResponse;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.model.VacancySnapshot;
import ru.workbit.vacancy.model.mapper.VacancyMapper;
import ru.workbit.vacancy.repository.VacancySnapshotRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VacancyServiceTest")
class VacancyServiceTest {

    @Mock
    HhClient hhClient;
    @Mock
    VacancySnapshotRepository vacancySnapshotRepository;
    @Mock
    VacancyMapper vacancyMapper;

    @InjectMocks
    VacancyService vacancyService;

    private VacancySnapshot aSnapshot(UUID id) {
        return VacancySnapshot.builder()
                .id(id)
                .name("Java-разработчик")
                .employer("ООО Ромашка")
                .url("https://hh.ru/vacancy/123")
                .experience("От 3 до 6 лет")
                .description("Описание вакансии")
                .build();
    }

    @Nested
    @DisplayName("GetSnapshotView")
    class GetSnapshotView {

        @Test
        @DisplayName("Возвращает представление снапшота по id")
        void returnsSnapshotViewById() {
            // given
            UUID id = UUID.randomUUID();
            VacancySnapshot snapshot = aSnapshot(id);
            when(vacancySnapshotRepository.findById(id)).thenReturn(Optional.of(snapshot));

            VacancySnapshotView expectedView = new VacancySnapshotView(
                    "Java-разработчик", "ООО Ромашка", "https://hh.ru/vacancy/123", "От 3 до 6 лет");
            when(vacancyMapper.toSnapshotView(snapshot)).thenReturn(expectedView);

            // when
            var result = vacancyService.getSnapshotView(id);

            // then
            assertThat(result).isEqualTo(expectedView);
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда снапшот не найден")
        void throwsWhenSnapshotNotFound() {
            // given
            UUID id = UUID.randomUUID();
            when(vacancySnapshotRepository.findById(id)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> vacancyService.getSnapshotView(id))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Vacancy snapshot %s not found".formatted(id));

            verifyNoInteractions(vacancyMapper);
        }
    }

    @Nested
    @DisplayName("GetSnapshotViews")
    class GetSnapshotViews {

        @Test
        @DisplayName("Возвращает карту представлений снапшотов по их id")
        void returnsMapOfSnapshotViewsById() {
            // given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            VacancySnapshot snapshot1 = aSnapshot(id1);
            VacancySnapshot snapshot2 = aSnapshot(id2);
            when(vacancySnapshotRepository.findAllById(List.of(id1, id2)))
                    .thenReturn(List.of(snapshot1, snapshot2));

            VacancySnapshotView view1 = new VacancySnapshotView("Java-разработчик", "ООО Ромашка", "url1", "level1");
            VacancySnapshotView view2 = new VacancySnapshotView("Python-разработчик", "ООО Лютик", "url2", "level2");
            when(vacancyMapper.toSnapshotView(snapshot1)).thenReturn(view1);
            when(vacancyMapper.toSnapshotView(snapshot2)).thenReturn(view2);

            // when
            var result = vacancyService.getSnapshotViews(List.of(id1, id2));

            // then
            assertThat(result).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(id1, view1, id2, view2));
        }

        @Test
        @DisplayName("Возвращает пустую карту, когда список id пуст")
        void returnsEmptyMapWhenIdsEmpty() {
            // given
            when(vacancySnapshotRepository.findAllById(List.of())).thenReturn(List.of());

            // when
            var result = vacancyService.getSnapshotViews(List.of());

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(vacancyMapper);
        }
    }

    private HhVacancyResponse anHhVacancyResponse(boolean archived) {
        return new HhVacancyResponse(
                "Java-разработчик",
                new HhVacancyResponse.Employer("ООО Ромашка"),
                new HhVacancyResponse.Salary(100000, 200000, "RUR"),
                new HhVacancyResponse.Experience("От 3 до 6 лет"),
                List.of(new HhVacancyResponse.KeySkill("Java")),
                "<p>Описание</p>",
                archived);
    }

    @Nested
    @DisplayName("Fetch")
    class Fetch {

        @Test
        @DisplayName("Извлекает id вакансии из URL, получает вакансию с hh.ru и маппит в VacancyData")
        void fetchesActiveVacancyAndMapsToVacancyData() {
            // given
            HhVacancyResponse hhVacancy = anHhVacancyResponse(false);
            when(hhClient.getHhVacancy("123456")).thenReturn(hhVacancy);

            VacancyData expected = new VacancyData(123456L, "https://hh.ru/vacancy/123456", "Java-разработчик",
                    "ООО Ромашка", "От 3 до 6 лет", List.of("Java"), "Описание");
            when(vacancyMapper.toVacancyData(hhVacancy, 123456L, "https://hh.ru/vacancy/123456", "Описание"))
                    .thenReturn(expected);

            // when
            var result = vacancyService.fetch("https://hh.ru/vacancy/123456");

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда вакансия в архиве")
        void throwsWhenVacancyArchived() {
            // given
            when(hhClient.getHhVacancy("123456")).thenReturn(anHhVacancyResponse(true));

            // when / then
            assertThatThrownBy(() -> vacancyService.fetch("https://hh.ru/vacancy/123456"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Vacancy 123456 not found or archived");

            verifyNoInteractions(vacancyMapper);
        }

        @Test
        @DisplayName("Бросает IllegalArgumentException, когда URL не ссылка на вакансию hh.ru")
        void throwsWhenUrlIsNotHhVacancyLink() {
            // when / then
            assertThatThrownBy(() -> vacancyService.fetch("https://example.com/not-a-vacancy"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("URL is not a hh.ru vacancy link: https://example.com/not-a-vacancy");

            verifyNoInteractions(hhClient, vacancyMapper);
        }
    }

    @Nested
    @DisplayName("Preview")
    class Preview {

        @Test
        @DisplayName("Возвращает превью вакансии по URL")
        void returnsVacancyPreview() {
            // given
            HhVacancyResponse hhVacancy = anHhVacancyResponse(false);
            when(hhClient.getHhVacancy("123456")).thenReturn(hhVacancy);

            VacancyPreviewResponse expected = new VacancyPreviewResponse(
                    "Java-разработчик", "ООО Ромашка", "100 000 - 200 000 ₽", "От 3 до 6 лет",
                    "https://hh.ru/vacancy/123456");
            when(vacancyMapper.toPreview(hhVacancy, "https://hh.ru/vacancy/123456")).thenReturn(expected);

            // when
            var result = vacancyService.preview("https://hh.ru/vacancy/123456");

            // then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("SaveSnapshot")
    class SaveSnapshot {

        @Test
        @DisplayName("Сохраняет снапшот через маппер и возвращает его id")
        void savesSnapshotAndReturnsId() {
            // given
            VacancyData data = new VacancyData(123L, "https://hh.ru/vacancy/123", "Java-разработчик",
                    "ООО Ромашка", "От 3 до 6 лет", List.of("Java"), "Описание");
            UUID snapshotId = UUID.randomUUID();
            VacancySnapshot mappedSnapshot = aSnapshot(snapshotId);
            when(vacancyMapper.toSnapshot(data, "Java-разработчик")).thenReturn(mappedSnapshot);
            when(vacancySnapshotRepository.save(mappedSnapshot)).thenReturn(aSnapshot(snapshotId));

            // when
            var result = vacancyService.saveSnapshot(data, "Java-разработчик");

            // then
            assertThat(result).isEqualTo(snapshotId);
        }
    }
}
