package ru.workbit.exception;

/**
 * Ошибка обращения к hh.ru: недоступность, отказ в доступе или невалидный ответ.
 */
public class VacancyFetchException extends RuntimeException {
    public VacancyFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
