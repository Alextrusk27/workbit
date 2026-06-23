package ru.workbit.interview.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum Profession {
    JAVA_DEV("Java-разработчик"),
    PYTHON_DEV("Python-разработчик"),
    QA("Инженер по тестированию");

    @JsonValue
    private final String name;

    Profession(String name) {
        this.name = name;
    }
}
