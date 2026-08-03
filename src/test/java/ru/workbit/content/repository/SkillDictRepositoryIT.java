package ru.workbit.content.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.content.model.DictStatus;
import ru.workbit.content.model.ProfessionDict;
import ru.workbit.content.model.SkillDict;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("SkillDictRepositoryIT")
class SkillDictRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private SkillDictRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private ProfessionDict aProfession(String name) {
        return ProfessionDict.builder()
                .name(name)
                .build();
    }

    private SkillDict aSkill(UUID professionId, String name) {
        return SkillDict.builder()
                .professionId(professionId)
                .name(name)
                .build(); // status=AUTO, usageCount=0 — @Builder.Default
    }

    private SkillDict anApprovedSkill(UUID professionId, String name) {
        return SkillDict.builder()
                .professionId(professionId)
                .name(name)
                .status(DictStatus.APPROVED)
                .build();
    }

    private SkillDict anApprovedSkill(UUID professionId, String name, int usageCount) {
        return SkillDict.builder()
                .professionId(professionId)
                .name(name)
                .usageCount(usageCount)
                .status(DictStatus.APPROVED)
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("UniqueNamePerProfession")
    class UniqueNamePerProfession {

        @Test
        @DisplayName("Дубль имени навыка в другом регистре у той же профессии нарушает уникальность при flush")
        void throwsOnDuplicateNameSameProfessionDifferentCase() {
            // given
            var profession = em.persistAndFlush(aProfession("Java Developer"));
            em.persistAndFlush(aSkill(profession.getId(), "Spring Core"));

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(aSkill(profession.getId(), "spring core")))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Одинаковое имя навыка у разных профессий допустимо")
        void allowsSameNameForDifferentProfessions() {
            // given
            var professionA = em.persistAndFlush(aProfession("Java Developer"));
            var professionB = em.persistAndFlush(aProfession("Python Developer"));

            // when
            var skillA = em.persistAndFlush(aSkill(professionA.getId(), "Databases"));
            var skillB = em.persistAndFlush(aSkill(professionB.getId(), "Databases"));

            // then
            assertThat(skillA.getId()).isNotEqualTo(skillB.getId());
            assertThat(repository.findById(skillA.getId())).isPresent();
            assertThat(repository.findById(skillB.getId())).isPresent();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CheckConstraintStatus")
    class CheckConstraintStatus {

        @Test
        @DisplayName("Статус вне AUTO/APPROVED нарушает CHECK-констрейнт")
        void throwsWhenStatusInvalid() {
            // given
            var profession = em.persistAndFlush(aProfession("Check Status Profession"));

            // when / then — Java-энум DictStatus не позволяет собрать невалидное значение,
            // поэтому CHECK проверяем нативной вставкой в обход маппинга
            assertThatThrownBy(() -> em.getEntityManager()
                    .createNativeQuery("INSERT INTO content.skill_dict (id, profession_id, name, status) "
                            + "VALUES (gen_random_uuid(), :professionId, 'Bad Status Skill', 'BOGUS')")
                    .setParameter("professionId", profession.getId())
                    .executeUpdate())
                    .isInstanceOf(Exception.class);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("SaveAndRead")
    class SaveAndRead {

        @Test
        @DisplayName("UUID генерируется автоматически при сохранении")
        void uuidIsGeneratedOnSave() {
            // given
            var profession = em.persistAndFlush(aProfession("Uuid Gen Profession"));
            var skill = aSkill(profession.getId(), "Uuid Gen Skill");
            assertThat(skill.getId()).isNull();

            // when
            var saved = em.persistFlushFind(skill);

            // then
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("Дефолты status=AUTO и usageCount=0 применяются из @Builder.Default")
        void defaultsAreApplied() {
            // given
            var profession = em.persistAndFlush(aProfession("Defaults Profession"));

            // when
            var saved = em.persistFlushFind(aSkill(profession.getId(), "Defaults Skill"));

            // then
            assertThat(saved.getStatus()).isEqualTo(DictStatus.AUTO);
            assertThat(saved.getUsageCount()).isZero();
            assertThat(saved.getCreated()).isNotNull();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Suggest")
    class Suggest {

        @Test
        @DisplayName("Возвращаются только навыки указанной профессии")
        void returnsOnlySkillsOfGivenProfession() {
            // given
            var professionA = em.persistAndFlush(aProfession("Zzz Backend Developer"));
            var professionB = em.persistAndFlush(aProfession("Zzz Frontend Developer"));
            var skillA = em.persistAndFlush(anApprovedSkill(professionA.getId(), "Databases Basics"));
            em.persistAndFlush(anApprovedSkill(professionB.getId(), "Databases Basics"));

            // when
            var result = repository.suggest(professionA.getName(), "databases", 10);

            // then
            assertThat(result).extracting(SkillDict::getId).containsExactly(skillA.getId());
        }

        @Test
        @DisplayName("Профессия резолвится регистронезависимо")
        void resolvesProfessionCaseInsensitively() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Backend Developer"));
            var skill = em.persistAndFlush(anApprovedSkill(profession.getId(), "Rest Api Basics"));

            // when
            var result = repository.suggest("zzz BACKEND developer", "rest", 10);

            // then
            assertThat(result).extracting(SkillDict::getId).containsExactly(skill.getId());
        }

        @Test
        @DisplayName("Неизвестная профессия — пустой список")
        void unknownProfessionReturnsEmptyList() {
            // when
            var result = repository.suggest("Zzz Nonexistent Profession", "any", 10);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AUTO-навык не попадает в выдачу, даже если имя матчится")
        void autoStatusSkillIsExcluded() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Auto Skill Profession"));
            em.persistAndFlush(aSkill(profession.getId(), "Auto Only Skill"));

            // when
            var result = repository.suggest(profession.getName(), "auto only", 10);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Prefix-совпадения идут раньше substring-совпадений независимо от usage_count")
        void prefixMatchesRankBeforeSubstringMatches() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Ranking Profession"));
            em.persistAndFlush(anApprovedSkill(profession.getId(), "Backend Dev Guru", 100));
            em.persistAndFlush(anApprovedSkill(profession.getId(), "Dev Ninja", 1));

            // when
            var result = repository.suggest(profession.getName(), "dev", 10);

            // then
            assertThat(result).extracting(SkillDict::getName)
                    .containsExactly("Dev Ninja", "Backend Dev Guru");
        }

        @Test
        @DisplayName("При равном типе совпадения — порядок по usage_count DESC")
        void sameMatchTypeOrderedByUsageCountDesc() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Usage Profession"));
            em.persistAndFlush(anApprovedSkill(profession.getId(), "Qwe One", 1));
            em.persistAndFlush(anApprovedSkill(profession.getId(), "Qwe Two", 5));

            // when
            var result = repository.suggest(profession.getName(), "qwe", 10);

            // then
            assertThat(result).extracting(SkillDict::getName)
                    .containsExactly("Qwe Two", "Qwe One");
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("SuggestAcrossProfessions")
    class SuggestAcrossProfessions {

        @Test
        @DisplayName("Схлопывает одноимённые навыки разных профессий в одну запись")
        void collapsesSameNameSkillsAcrossProfessions() {
            // given
            var professionA = em.persistAndFlush(aProfession("Zzz Cross Profession A"));
            var professionB = em.persistAndFlush(aProfession("Zzz Cross Profession B"));
            em.persistAndFlush(anApprovedSkill(professionA.getId(), "Zzz Cross Skill", 2));
            em.persistAndFlush(anApprovedSkill(professionB.getId(), "Zzz Cross Skill", 3));

            // when
            var result = repository.suggestAcrossProfessions("zzz cross", 10);

            // then
            assertThat(result).containsExactly("Zzz Cross Skill");
        }

        @Test
        @DisplayName("Сортирует схлопнутые группы по суммарному usage_count по всем профессиям DESC")
        void ordersCollapsedGroupsBySummedUsageCountDesc() {
            // given
            var professionA = em.persistAndFlush(aProfession("Zzz Sum Profession A"));
            var professionB = em.persistAndFlush(aProfession("Zzz Sum Profession B"));
            em.persistAndFlush(anApprovedSkill(professionA.getId(), "Zzz Sum Alpha", 1));
            em.persistAndFlush(anApprovedSkill(professionA.getId(), "Zzz Sum Beta", 2));
            em.persistAndFlush(anApprovedSkill(professionB.getId(), "Zzz Sum Beta", 5));

            // when
            var result = repository.suggestAcrossProfessions("zzz sum", 10);

            // then — Beta: 2+5=7, Alpha: 1
            assertThat(result).containsExactly("Zzz Sum Beta", "Zzz Sum Alpha");
        }

        @Test
        @DisplayName("AUTO-навык не попадает в выдачу, даже если имя матчится")
        void autoStatusSkillIsExcluded() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Cross Auto Profession"));
            em.persistAndFlush(aSkill(profession.getId(), "Zzz Auto Cross Skill"));

            // when
            var result = repository.suggestAcrossProfessions("zzz auto cross", 10);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Prefix-совпадения идут раньше substring-совпадений независимо от usage_count")
        void prefixMatchesRankBeforeSubstringMatches() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Cross Ranking Profession"));
            em.persistAndFlush(anApprovedSkill(profession.getId(), "Backend Dev Guru Cross", 100));
            em.persistAndFlush(anApprovedSkill(profession.getId(), "Dev Ninja Cross", 1));

            // when
            var result = repository.suggestAcrossProfessions("dev", 10);

            // then
            assertThat(result).containsExactly("Dev Ninja Cross", "Backend Dev Guru Cross");
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindTopNames")
    class FindTopNames {

        @Test
        @DisplayName("Схлопывает одноимённые навыки разных профессий и суммирует usage_count")
        void collapsesSameNameSkillsAcrossProfessions() {
            // given
            var professionA = em.persistAndFlush(aProfession("Zzz Top Profession A"));
            var professionB = em.persistAndFlush(aProfession("Zzz Top Profession B"));
            em.persistAndFlush(anApprovedSkill(professionA.getId(), "Zzz Top Cross", 2));
            em.persistAndFlush(anApprovedSkill(professionB.getId(), "Zzz Top Cross", 3));

            // when
            var result = repository.findTopNames(50);

            // then
            assertThat(result).containsExactly("Zzz Top Cross");
        }

        @Test
        @DisplayName("Сортирует по суммарному usage_count DESC, затем по имени")
        void ordersBySummedUsageCountDesc() {
            // given
            var professionA = em.persistAndFlush(aProfession("Zzz Rank Profession A"));
            var professionB = em.persistAndFlush(aProfession("Zzz Rank Profession B"));
            em.persistAndFlush(anApprovedSkill(professionA.getId(), "Zzz Rank Low", 1));
            em.persistAndFlush(anApprovedSkill(professionA.getId(), "Zzz Rank High", 2));
            em.persistAndFlush(anApprovedSkill(professionB.getId(), "Zzz Rank High", 5));

            // when
            var result = repository.findTopNames(50);

            // then — High: 2+5=7, Low: 1
            assertThat(result).containsExactly("Zzz Rank High", "Zzz Rank Low");
        }

        @Test
        @DisplayName("AUTO-навыки исключены из топа")
        void autoStatusSkillsAreExcluded() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Top Auto Profession"));
            em.persistAndFlush(aSkill(profession.getId(), "Zzz Auto Top Skill"));

            // when
            var result = repository.findTopNames(50);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Limit обрезает количество результатов")
        void limitCutsResults() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Lim Profession"));
            em.persistAndFlush(anApprovedSkill(profession.getId(), "Lim One", 1));
            em.persistAndFlush(anApprovedSkill(profession.getId(), "Lim Two", 2));
            em.persistAndFlush(anApprovedSkill(profession.getId(), "Lim Three", 3));

            // when
            var result = repository.findTopNames(2);

            // then
            assertThat(result).containsExactly("Lim Three", "Lim Two");
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("ExistsByProfessionIdAndNameIgnoreCaseAndStatus")
    class ExistsByProfessionIdAndNameIgnoreCaseAndStatus {

        @Test
        @DisplayName("True для APPROVED-навыка своей профессии при другом регистре имени")
        void trueForApprovedSkillDifferentCase() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Exists Profession"));
            em.persistAndFlush(anApprovedSkill(profession.getId(), "Rest Api Basics"));

            // when
            var result = repository.existsByProfessionIdAndNameIgnoreCaseAndStatus(
                    profession.getId(), "REST api basics", DictStatus.APPROVED);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("False, если навык есть, но статус AUTO")
        void falseWhenSkillIsAutoStatus() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Exists Auto Profession"));
            em.persistAndFlush(aSkill(profession.getId(), "Auto Only Skill"));

            // when
            var result = repository.existsByProfessionIdAndNameIgnoreCaseAndStatus(
                    profession.getId(), "auto only skill", DictStatus.APPROVED);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("False для того же навыка, но другого professionId")
        void falseForSameSkillDifferentProfessionId() {
            // given
            var professionA = em.persistAndFlush(aProfession("Zzz Exists Profession A"));
            var professionB = em.persistAndFlush(aProfession("Zzz Exists Profession B"));
            em.persistAndFlush(anApprovedSkill(professionA.getId(), "Databases Basics"));

            // when
            var result = repository.existsByProfessionIdAndNameIgnoreCaseAndStatus(
                    professionB.getId(), "Databases Basics", DictStatus.APPROVED);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("False для отсутствующего имени")
        void falseForMissingName() {
            // given
            var profession = em.persistAndFlush(aProfession("Zzz Exists Missing Profession"));

            // when
            var result = repository.existsByProfessionIdAndNameIgnoreCaseAndStatus(
                    profession.getId(), "Zzz Nonexistent Skill Name", DictStatus.APPROVED);

            // then
            assertThat(result).isFalse();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("UpsertAndIncrementUsage")
    class UpsertAndIncrementUsage {

        @Test
        @DisplayName("Новый навык создаётся со status AUTO, usage_count 1 и привязкой к профессии")
        void newSkillCreatesRowWithDefaults() {
            // given
            var profession = em.persistAndFlush(aProfession("Kotlin Engineer"));

            // when
            var returnedId = repository.upsertAndIncrementUsage(profession.getId(), "Kotlin Basics");

            // then
            var saved = repository.findById(returnedId).orElseThrow();
            assertThat(saved.getProfessionId()).isEqualTo(profession.getId());
            assertThat(saved.getName()).isEqualTo("Kotlin Basics");
            assertThat(saved.getStatus()).isEqualTo(DictStatus.AUTO);
            assertThat(saved.getUsageCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Повторный вызов с тем же именем в другом регистре у той же профессии инкрементит usage_count и не меняет каноническое имя")
        void repeatedCallDifferentCaseIncrementsUsageSameProfession() {
            // given
            var profession = em.persistAndFlush(aProfession("Scala Engineer"));
            var firstId = repository.upsertAndIncrementUsage(profession.getId(), "Coroutines");

            // when
            var secondId = repository.upsertAndIncrementUsage(profession.getId(), "coroutines");

            // then
            assertThat(secondId).isEqualTo(firstId);
            var saved = repository.findById(firstId).orElseThrow();
            assertThat(saved.getName()).isEqualTo("Coroutines");
            assertThat(saved.getUsageCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Одинаковое имя навыка у разных профессий создаёт две независимые строки")
        void sameSkillNameDifferentProfessionsCreatesIndependentRows() {
            // given
            var professionA = em.persistAndFlush(aProfession("Zzz Upsert Profession A"));
            var professionB = em.persistAndFlush(aProfession("Zzz Upsert Profession B"));

            // when
            var idA = repository.upsertAndIncrementUsage(professionA.getId(), "Testing Basics");
            var idB = repository.upsertAndIncrementUsage(professionB.getId(), "Testing Basics");

            // then
            assertThat(idA).isNotEqualTo(idB);
            var savedA = repository.findById(idA).orElseThrow();
            var savedB = repository.findById(idB).orElseThrow();
            assertThat(savedA.getProfessionId()).isEqualTo(professionA.getId());
            assertThat(savedA.getUsageCount()).isEqualTo(1);
            assertThat(savedB.getProfessionId()).isEqualTo(professionB.getId());
            assertThat(savedB.getUsageCount()).isEqualTo(1);
        }
    }
}
