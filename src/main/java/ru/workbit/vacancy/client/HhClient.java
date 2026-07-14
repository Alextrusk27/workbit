package ru.workbit.vacancy.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.exception.VacancyFetchException;
import ru.workbit.vacancy.dto.HhVacancyResponse;

@Component
@RequiredArgsConstructor
public class HhClient {
    private final RestClient hhRestClient;

    public HhVacancyResponse getHhVacancy(String vacancyId) {
        try {
            return hhRestClient.get()
                    .uri("vacancies/{id}", vacancyId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new NotFoundException("Vacancy %s not found".formatted(vacancyId));
                    })
                    .body(HhVacancyResponse.class);
        } catch (RestClientException e) {
            throw new VacancyFetchException("Failed to fetch vacancy %s from hh.ru".formatted(vacancyId), e);
        }
    }
}
