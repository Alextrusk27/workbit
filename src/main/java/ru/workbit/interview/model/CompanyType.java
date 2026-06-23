package ru.workbit.interview.model;

import lombok.Getter;

@Getter
public enum CompanyType {
    BANK("Банк"),
    FINTECH("Финтех"),
    STARTUP("Стартап"),
    PRODUCT("Продуктовая компания"),
    OUTSOURCE("Аутсорс"),
    GOV("Государственная компания");

    private final String name;

    CompanyType(String name) {
        this.name = name;
    }
}
