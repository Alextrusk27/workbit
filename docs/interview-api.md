# Interview API — `/api/v1/interview`

Тренировочное собеседование: пользователь создаёт сессию по выбранной профессии, уровню
и типу компании, получает набор вопросов, отвечает на них и в конце получает итоговый
отчёт с оценкой и вероятностью оффера. Оценку ответов и финальный отчёт формирует LLM.
Все тела запросов и ответов — JSON.

## Доступ

Весь домен закрыт: `SecurityConfig` требует аутентификации на любой путь, не входящий
в `/api/v1/auth/**` и Swagger. Значит, ко всем ручкам `/api/v1/interview/**` нужен
валидный access-токен — cookie `access_token` (для Swagger в dev поддержан fallback
`Authorization: Bearer <accessToken>`). Без него — `401` с пустым телом (это делает
Security-фильтр, а не контроллер, поэтому тело `ApiError` не приходит).

Все сессии и вопросы привязаны к пользователю из токена: чужие ресурсы либо не находятся
(`404`), либо явно отклоняются (`403`). Пользователь не может ни увидеть, ни изменить
сессию другого пользователя.

## Эндпоинты

| Метод | Путь | Назначение | Авторизация |
|---|---|---|---|
| GET | `/options` | справочник допустимых профессий/уровней/типов компании и границ числа вопросов | cookie `access_token` |
| POST | `/sessions` | создать сессию с набором вопросов | cookie `access_token` |
| GET | `/sessions` | список всех сессий пользователя | cookie `access_token` |
| GET | `/sessions/{sessionId}` | получить сессию по id | cookie `access_token` |
| GET | `/sessions/{sessionId}/continue` | следующий неотвеченный вопрос | cookie `access_token` |
| GET | `/sessions/{sessionId}/questions/{index}` | вопрос по порядковому индексу (1-based) | cookie `access_token` |
| POST | `/sessions/{sessionId}/questions/{questionId}` | отправить ответ на вопрос (опц. LLM-оценка) | cookie `access_token` |
| POST | `/sessions/{sessionId}/finish` | завершить сессию и сформировать отчёт | cookie `access_token` |
| GET | `/sessions/{sessionId}/report` | получить ранее сформированный отчёт | cookie `access_token` |
| DELETE | `/sessions/{sessionId}` | удалить сессию вместе с вопросами | cookie `access_token` |

## Справочные значения (enum'ы)

Enum'ы сериализуются не по имени константы, а по русскому лейблу (`@JsonValue`). В теле
запроса и ответа фигурирует именно лейбл. Чтобы не хардкодить их на фронте, есть
`GET /options`.

- **Profession** — `Java-разработчик`, `Python-разработчик`, `Инженер по тестированию`.
- **Level** — `Junior`, `Middle`, `Senior`, `Lead`.
- **CompanyType** — `Банк`, `Финтех`, `Стартап`, `Продуктовая компания`, `Аутсорс`,
  `Государственная компания`.
- **SessionStatus** — `CREATED`, `IN_PROGRESS`, `COMPLETED` (без кастомного лейбла,
  сериализуется по имени константы).
- **OfferProbability** — лейблы `Низкая`/`Средняя`/`Высокая` (поле `offerProbability`
  в отчёте).

## Запросы (DTO)

```java
record CreateSessionRequest(
    @NotNull Profession profession,
    @NotNull Level level,
    @NotNull CompanyType companyType,
    @NotNull @Min(10) @Max(20) Integer totalQuestions)

record SubmitAnswerBody(@NotBlank String answerText)
```

- `CreateSessionRequest` — все поля обязательны. `totalQuestions` — от 10 до 20
  включительно. Значения enum'ов передаются лейблами (например `"profession": "Java-разработчик"`).
- `SubmitAnswerBody` — единственное поле `answerText`, непустое.

`GET /options`, `finish` и `delete` тела не принимают.

### Query-параметры

- `POST /sessions/{sessionId}/questions/{questionId}` принимает `?evaluate=<bool>`
  (по умолчанию `false`). При `evaluate=true` ответ синхронно отправляется в LLM и
  сразу получает оценку и фидбэк; при `false` ответ просто сохраняется, а оценка
  выставится позже, при завершении сессии.

## Ответы

```java
record InterviewOptionsResponse(
    List<Profession> professions, List<Level> levels, List<CompanyType> companyTypes,
    int minQuestions, int maxQuestions)

record SessionResponse(
    UUID id, Profession profession, CompanyType companyType, Level level,
    SessionStatus status, int totalQuestions, int answeredCount,
    Instant created, Instant completedAt)

record QuestionResponse(
    UUID questionId, int orderIndex, String questionText,
    String answerText, Integer score, String feedback)

record SessionReport(
    UUID reportId, UUID sessionId, Profession profession, CompanyType companyType,
    Level level, Integer totalQuestions, Double avgScore, String overallFeedback,
    OfferProbability offerProbability, Instant generatedAt)
```

- **`GET /options`** — `200`, `InterviewOptionsResponse`.
- **`POST /sessions`** — `201` с `SessionResponse` и заголовком `Location`
  (`/sessions/{id}`). Только что созданная сессия имеет статус `CREATED` и `answeredCount = 0`.
- **`GET /sessions`** — `200`, массив `SessionResponse` (может быть пустым).
- **`GET /sessions/{sessionId}`** — `200`, `SessionResponse` с актуальным `answeredCount`.
- **`GET .../continue`** и **`GET .../questions/{index}`** — `200`, `QuestionResponse`.
  Поля `answerText`, `score`, `feedback` равны `null`, пока по вопросу нет ответа/оценки.
- **`POST .../questions/{questionId}`** — `200`, `QuestionResponse` с сохранённым ответом
  (и оценкой/фидбэком, если `evaluate=true`).
- **`POST .../finish`** — `201` с `SessionReport` и заголовком `Location`
  (`/sessions/{id}/report`).
- **`GET .../report`** — `200`, `SessionReport`.
- **`DELETE /sessions/{sessionId}`** — `204`, пустое тело.

## Поведение

- **Создание сессии.** Вопросы берутся из внутреннего банка вопросов под выбранный
  уровень в количестве `totalQuestions`, нумеруются `orderIndex` от 1. Если банк не
  вернул ни одного вопроса — `500` (сессия не создаётся).
- **Прогресс сессии.** Статус меняется автоматически: `CREATED` → `IN_PROGRESS` при
  первом сохранённом ответе, `COMPLETED` — при `finish`. `answeredCount` в
  `SessionResponse` считается по числу отвеченных вопросов.
- **`continue`** возвращает первый неотвеченный вопрос по порядку. Когда неотвеченных
  не осталось — `404` («This session finished»); это штатный сигнал «сессия пройдена»,
  а не ошибка данных.
- **Индекс вопроса** в `.../questions/{index}` — порядковый номер (1-based), а не UUID.
  Отсутствующий индекс — `404`.
- **Отправка ответа.** Вопрос ищется по `questionId`; проверяются владелец (иначе `403`),
  принадлежность указанной в пути сессии (иначе `409`) и то, что вопрос ещё не отвечен
  (иначе `409`). Повторно ответить на вопрос нельзя. При `evaluate=true` идёт синхронный
  вызов LLM — ручка отвечает медленнее.
- **Завершение сессии.** `finish` собирает все вопросы с ответами, запрашивает у LLM
  итоговый отчёт: для ответов без индивидуальной оценки (тех, что сохраняли с
  `evaluate=false`) выставляются оценки из отчёта, считается средний балл (округляется
  до одного знака), фиксируются общий фидбэк и вероятность оффера. Статус переходит в
  `COMPLETED`, проставляется `completedAt`. Отчёт после этого доступен через
  `GET .../report`. Повторный `finish` пересоберёт отчёт заново.
- **Удаление** — физическое: сессия удаляется вместе с вопросами (не soft delete,
  в отличие от домена пользователей).

## Ошибки

Тело ошибки — стандартный `ApiError`:

```json
{
  "timestamp": "2026-07-02T12:00:00",
  "status": "NOT_FOUND",
  "message": "The required object was not found.",
  "errors": ["Session not found"]
}
```

| Код | Когда |
|---|---|
| `400` | невалидное тело запроса: нарушены ограничения полей (`totalQuestions` вне 10..20, пустой `answerText`, отсутствующий enum), битый JSON, неверный тип path/query-параметра |
| `401` | нет валидного access-токена (ставит Security-фильтр, тело пустое) |
| `403` | вопрос или сессия принадлежат другому пользователю |
| `404` | сессия или вопрос не найдены (в т.ч. чужие); `continue` при отсутствии неотвеченных вопросов |
| `409` | вопрос уже отвечен либо не принадлежит указанной в пути сессии |
| `500` | банк вопросов не вернул вопросов при создании сессии |
| `503` | недоступен LLM-сервис (при оценке ответа с `evaluate=true` или формировании отчёта в `finish`) |

`503` отдаётся глобальным обработчиком `LlmException` (`AI service unavailable.`) —
достижим на ручках, дергающих LLM: `POST .../questions/{questionId}?evaluate=true`
и `POST .../finish`.
