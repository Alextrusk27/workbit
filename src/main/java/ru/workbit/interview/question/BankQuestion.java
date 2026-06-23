package ru.workbit.interview.question;

import ru.workbit.interview.model.Category;
import ru.workbit.interview.model.Level;

/**
 * Запись банка вопросов из ресурса. Поля совпадают с ключами JSON.
 */
public record BankQuestion(Category category, Level level, String text) {
}
