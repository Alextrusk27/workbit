package ru.workbit.vacancy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record HhVacancyResponse(
        String name,
        Employer employer,
        Salary salary,
        Experience experience,
        @JsonProperty("key_skills") List<KeySkill> keySkills,
        String description,
        boolean archived
) {
    public record Employer(String name, @JsonProperty("logo_urls") LogoUrls logoUrls) {
    }

    /**
     * Логотипы работодателя с hh.ru: ключи - ширина в пикселях плюс оригинал.
     * У части работодателей логотипа нет, тогда блок приходит null.
     */
    public record LogoUrls(@JsonProperty("240") String size240, String original) {
    }

    public record Salary(Integer from, Integer to, String currency) {
    }

    public record Experience(String name) {
    }

    public record KeySkill(String name) {
    }
}
