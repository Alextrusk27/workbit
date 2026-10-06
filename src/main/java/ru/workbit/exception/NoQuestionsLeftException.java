package ru.workbit.exception;

public class NoQuestionsLeftException extends ConflictException {
    public NoQuestionsLeftException(String message) {
        super(message);
    }
}
