package ru.workbit.interview.model;

import lombok.Getter;

@Getter
public enum Category {
    JAVA_CORE("Java Core", Domain.JAVA),
    CONCURRENCY("Многопоточность", Domain.JAVA),
    SPRING("Spring", Domain.JAVA),
    SPRING_BOOT("Spring Boot", Domain.JAVA),
    SQL_JPA("SQL / JPA", Domain.JAVA),
    TRANSACTIONS("Транзакции", Domain.JAVA),
    XML_SOAP("XML / SOAP", Domain.JAVA),
    LEGACY_INTEGRATION("Интеграция с legacy", Domain.JAVA),

    PYTHON_CORE("Python Core", Domain.PYTHON),
    ASYNCIO("asyncio", Domain.PYTHON),
    DJANGO("Django", Domain.PYTHON),
    FASTAPI("FastAPI", Domain.PYTHON),
    ORM_SQL("ORM / SQL", Domain.PYTHON),
    DATA_PROCESSING("Обработка данных", Domain.PYTHON),

    TEST_DESIGN("Дизайн тест-кейсов", Domain.QA),
    TEST_AUTOMATION("Автоматизация тестирования", Domain.QA),
    MANUAL_TESTING("Ручное тестирование", Domain.QA),
    API_TESTING("Тестирование API", Domain.QA),
    PERFORMANCE_TESTING("Нагрузочное тестирование", Domain.QA);

    private final String name;
    private final Domain domain;

    Category(String name, Domain java) {
        this.name = name;
        this.domain = java;
    }

    public enum Domain {
        JAVA,
        PYTHON,
        QA
    }
}
