package ru.workbit.interview.question;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ru.workbit.interview.model.Category;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Готовый банк вопросов, загружаемый из ресурса один раз при старте и сгруппированный по категории.
 */
@Component
public class QuestionBank {

    private static final String RESOURCE = "interview/questions-java.json";

    private final Map<Category, List<BankQuestion>> byCategory;

    public QuestionBank(ObjectMapper objectMapper) {
        List<BankQuestion> all = load(objectMapper);
        this.byCategory = all.stream().collect(Collectors.groupingBy(BankQuestion::category));
    }

    private static List<BankQuestion> load(ObjectMapper objectMapper) {
        try (InputStream is = new ClassPathResource(RESOURCE).getInputStream()) {
            return objectMapper.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось загрузить банк вопросов: " + RESOURCE, e);
        }
    }

    /** Все вопросы заданной категории (пустой список, если категории нет в банке). */
    public List<BankQuestion> forCategory(Category category) {
        return byCategory.getOrDefault(category, List.of());
    }
}
