package ru.workbit.exception;

/**
 * Ошибка обращения к LLM: недоступность, таймаут или невалидный ответ.
 */
public class LlmException extends RuntimeException {
    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmException(String message) {
        super(message);
    }
}
