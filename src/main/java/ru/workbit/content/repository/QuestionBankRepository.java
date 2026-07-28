package ru.workbit.content.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.content.model.BankQuestion;

import java.util.List;
import java.util.UUID;

public interface QuestionBankRepository extends JpaRepository<@NotNull BankQuestion, @NotNull UUID> {

    @Query(value = """
            SELECT qb.* FROM content.question_bank qb
            WHERE qb.profession_id = :professionId
              AND qb.topic_id IS NOT DISTINCT FROM CAST(:topicId AS uuid)
              AND :level = ANY(qb.levels)
              AND NOT EXISTS (
                  SELECT 1
                  FROM training.question tq
                  JOIN training.session ts ON ts.id = tq.session_id
                  WHERE tq.bank_question_id = qb.id
                    AND ts.user_id = :userId)
            ORDER BY random()
            LIMIT :limit
            """, nativeQuery = true)
    List<BankQuestion> sampleUnseen(UUID professionId, UUID topicId, String level, UUID userId, int limit);
}
