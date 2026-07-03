package ru.workbit.interview.question;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ru.workbit.interview.model.Level;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Готовый банк вопросов, загружаемый из ресурса один раз при старте и сгруппированный по уровням.
 */
@Component
public class QuestionBank {

    private static final String RESOURCE = "interview/questions-java.json";

    private final Map<Level, List<BankQuestion>> byLevel;

    public QuestionBank(ObjectMapper objectMapper) {
        List<BankQuestion> all = load(objectMapper);
        this.byLevel = all.stream().collect(Collectors.groupingBy(BankQuestion::level));
    }

    private static List<BankQuestion> load(ObjectMapper objectMapper) {
        try (InputStream is = new ClassPathResource(RESOURCE).getInputStream()) {
            return objectMapper.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось загрузить банк вопросов: " + RESOURCE, e);
        }
    }

    public List<BankQuestion> forLevel(Level level, int quantity) {
        List<BankQuestion> questions = new ArrayList<>(byLevel.getOrDefault(level, List.of()));
        Collections.shuffle(questions);

        return questions.stream()
                .limit(quantity)
                .toList();
    }
}
