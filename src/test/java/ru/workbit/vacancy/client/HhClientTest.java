package ru.workbit.vacancy.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.exception.VacancyFetchException;
import ru.workbit.vacancy.config.HhClientConfig;
import ru.workbit.vacancy.config.HhProperties;
import ru.workbit.vacancy.dto.HhVacancyResponse;

@DisplayName("HhClientTest")
class HhClientTest {

    private static final String BASE_URL = "https://hh.test/";
    private static final String VACANCY_ID = "137090289";
    private static final String VACANCY_URL = BASE_URL + "vacancies/" + VACANCY_ID;
    private static final String FETCH_FAILED_MESSAGE = "Failed to fetch vacancy 137090289 from hh.ru";

    private MockRestServiceServer server;
    private HhClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var props = new HhProperties(BASE_URL, "workbit-test", "test-token");
        client = new HhClient(new HhClientConfig().hhRestClient(builder, props));
    }

    @Nested
    @DisplayName("GetHhVacancy")
    class GetHhVacancy {

        @Test
        @DisplayName("Возвращает разобранную вакансию, запрос уходит на vacancies/{id}")
        void returnsParsedVacancy() {
            // given
            var body = """
                    {"name": "Java-разработчик", "employer": {"name": "Acme"}, "archived": false,
                     "key_skills": [{"name": "Spring"}]}
                    """;
            server.expect(requestTo(VACANCY_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            // when
            var result = client.getHhVacancy(VACANCY_ID);

            // then
            assertThat(result.name()).isEqualTo("Java-разработчик");
            assertThat(result.employer().name()).isEqualTo("Acme");
            assertThat(result.keySkills()).extracting(HhVacancyResponse.KeySkill::name).containsExactly("Spring");
            assertThat(result.archived()).isFalse();
            server.verify();
        }

        @Test
        @DisplayName("Бросает NotFoundException на 404")
        void throwsNotFoundOn404() {
            // given
            server.expect(requestTo(VACANCY_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

            // when / then
            assertThatThrownBy(() -> client.getHhVacancy(VACANCY_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Vacancy 137090289 not found");
        }

        @ParameterizedTest
        @ValueSource(ints = {403, 500})
        @DisplayName("Бросает VacancyFetchException с кодом ответа в сообщении на HTTP-ошибку, кроме 404")
        void throwsFetchExceptionWithStatusOnHttpError(int status) {
            // given
            server.expect(requestTo(VACANCY_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

            // when / then
            assertThatThrownBy(() -> client.getHhVacancy(VACANCY_ID))
                    .isInstanceOf(VacancyFetchException.class)
                    .hasMessage(FETCH_FAILED_MESSAGE + ": status " + status)
                    .hasMessageEndingWith("status " + status)
                    .hasCauseInstanceOf(RestClientResponseException.class);
        }

        @Test
        @DisplayName("Бросает VacancyFetchException без статуса при сетевой ошибке")
        void throwsFetchExceptionWithoutStatusOnIoError() {
            // given
            server.expect(requestTo(VACANCY_URL)).andRespond(withException(new IOException("connection reset")));

            // when / then
            assertThatThrownBy(() -> client.getHhVacancy(VACANCY_ID))
                    .isInstanceOf(VacancyFetchException.class)
                    .hasMessage(FETCH_FAILED_MESSAGE)
                    .hasCauseInstanceOf(RestClientException.class)
                    .cause().isNotInstanceOf(RestClientResponseException.class);
        }

        @Test
        @DisplayName("Бросает VacancyFetchException без статуса при невалидном теле ответа")
        void throwsFetchExceptionWithoutStatusOnInvalidBody() {
            // given
            server.expect(requestTo(VACANCY_URL))
                    .andRespond(withSuccess("not a json", MediaType.APPLICATION_JSON));

            // when / then
            assertThatThrownBy(() -> client.getHhVacancy(VACANCY_ID))
                    .isInstanceOf(VacancyFetchException.class)
                    .hasMessage(FETCH_FAILED_MESSAGE)
                    .cause().isInstanceOf(RestClientException.class)
                    .isNotInstanceOf(RestClientResponseException.class);
        }
    }
}
