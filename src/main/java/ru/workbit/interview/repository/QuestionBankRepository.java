package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.interview.model.BankQuestion;
import ru.workbit.interview.model.Level;

import java.util.List;
import java.util.UUID;

public interface QuestionBankRepository extends JpaRepository<@NotNull BankQuestion, @NotNull UUID> {

    @Query("""
            SELECT b FROM BankQuestion b
            WHERE b.level = :level
            ORDER BY random()
            LIMIT :quantity
            """)
    List<BankQuestion> pickRandomByLevel(Level level, int quantity);


}
