# Interview API — `/api/v1/interview`

Тренировочное собеседование: пользователь создаёт сессию, получает набор вопросов,
отвечает на них и в конце получает итоговый отчёт с оценкой и вероятностью оффера.
Оценку ответов и финальный отчёт формирует LLM. Есть два источника вопросов: каталог
(вопросы подобраны заранее под профессию/уровень/тип компании) и вакансия (вопросы
генерирует LLM под конкретную вакансию hh.ru или вставленный текст). Все тела запросов
и ответов — JSON.

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
| POST | `/sessions` | создать каталожную сессию с набором вопросов | cookie `access_token` |
| POST | `/sessions/by-vacancy` | создать сессию с вопросами, сгенерированными LLM под вакансию | cookie `access_token` |
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
- **SessionSource** — `CATALOG`, `VACANCY` (без кастомного лейбла, сериализуется по
  имени константы). Показывает, откуда взяты вопросы сессии — из каталога или
  сгенерированы под вакансию.
- **OfferProbability** — лейблы `Низкая`/`Средняя`/`Высокая` (поле `offerProbability`
  в отчёте).

## Запросы (DTO)

```java
record CreateSessionRequest(
    @NotNull Profession profession,
    @NotNull Level level,
    @NotNull CompanyType companyType,
    @NotNull @Min(10) @Max(20) Integer totalQuestions)

record CreateSessionByVacancyRequest(
    String vacancyUrl,
    @Size(min = 50, max = 20000) String vacancyText,
    @NotNull @Min(10) @Max(20) Integer totalQuestions)

record SubmitAnswerBody(@NotBlank String answerText)
```

- `CreateSessionRequest` — все поля обязательны. `totalQuestions` — от 10 до 20
  включительно. Значения enum'ов передаются лейблами (например `"profession": "Java-разработчик"`).
- `CreateSessionByVacancyRequest` — `totalQuestions` обязателен, диапазон тот же (10..20).
  `vacancyUrl` и `vacancyText` сами по себе не обязательны, но действует инвариант
  «ровно один из двух»: должно быть заполнено (непустой, не пробельный) либо `vacancyUrl`,
  либо `vacancyText`, но не оба сразу и не ни одного. Нарушение — `400`. Если выбран
  `vacancyText`, на него действует ограничение длины 50..20000 символов; при выборе
  `vacancyUrl` ограничение длины не проверяется, зато ссылка должна вести на конкретную
  вакансию hh.ru (`https://hh.ru/vacancy/<id>`) — иначе тоже `400`.
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
    SessionSource source, VacancyInfo vacancy,
    SessionStatus status, int totalQuestions, int answeredCount,
    Instant created, Instant completedAt)

record VacancyInfo(String name, String employer, String url) // вложен в SessionResponse

record QuestionResponse(
    UUID questionId, int orderIndex, String questionText,
    String answerText, Integer score, String feedback)

record SessionReport(
    UUID reportId, UUID sessionId, Profession profession, CompanyType companyType,
    Level level, Integer totalQuestions, Double avgScore, String overallFeedback,
    OfferProbability offerProbability, Instant generatedAt)
```

`SessionResponse` возвращают все ручки, отдающие сессию (`POST /sessions`,
`POST /sessions/by-vacancy`, `GET /sessions`, `GET /sessions/{sessionId}`).
Поля `profession`, `companyType` и `level` заполнены только у каталожных сессий
(`source = CATALOG`), для сессий по вакансии (`source = VACANCY`) они `null`. И наоборот:
`vacancy` заполнен только у сессий по вакансии, для каталожных — `null`. У `VacancyInfo`
`employer` и `url` — `null`, если вакансия была задана вставленным текстом (`vacancyText`),
а не ссылкой на hh.ru: у текста нет ни работодателя, ни канонической ссылки. По той же
причине `profession`, `companyType` и `level` в `SessionReport` (`finish`, `.../report`)
для сессий по вакансии тоже `null` — отчёт строится без каталожной связки.

- **`GET /options`** — `200`, `InterviewOptionsResponse`.
- **`POST /sessions`** — `201` с `SessionResponse` и заголовком `Location`
  (`/sessions/{id}`). Только что созданная сессия имеет статус `CREATED` и `answeredCount = 0`,
  `source = CATALOG`, `vacancy = null`.
- **`POST /sessions/by-vacancy`** — `201` с `SessionResponse` и заголовком `Location`
  (`/sessions/{id}`), как и у каталожной сессии. `source = VACANCY`, `profession`,
  `companyType` и `level` равны `null` (у вакансии нет каталожной связки), а `vacancy`
  заполнен данными вакансии.
- **`GET /sessions`** — `200`, массив `SessionResponse` (может быть пустым; в нём
  вперемешку каталожные сессии и сессии по вакансии, различить их можно по `source`).
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

- **Создание каталожной сессии.** Вопросы берутся из внутреннего банка вопросов под
  выбранный уровень в количестве `totalQuestions`, нумеруются `orderIndex` от 1. Если банк
  не вернул ни одного вопроса — `500` (сессия не создаётся).
- **Создание сессии по вакансии.** `POST /sessions/by-vacancy` получает данные вакансии
  одним из двух способов: по `vacancyUrl` они подтягиваются с hh.ru (вакансия должна
  существовать и не быть в архиве), по `vacancyText` — берётся как есть вставленный
  пользователем текст (без запроса к hh.ru). Дальше название, работодатель, требуемый
  опыт, ключевые навыки и описание уходят в LLM, которая генерирует вопросы. Если LLM
  вернула вопросов больше, чем запрошено в `totalQuestions`, — лишние отбрасываются; если
  меньше — сессия создаётся с тем количеством, что фактически сгенерировано (в лог пишется
  предупреждение), поэтому `totalQuestions` в ответе не всегда точно совпадает с
  запрошенным значением. Если LLM не вернула ни одного вопроса — `503`, сессия не создаётся.
- **Снимок вакансии.** Данные вакансии на момент создания сессии сохраняются отдельным
  неизменяемым снимком; блок `vacancy` в `SessionResponse` строится по этому снимку, а не
  живым запросом к hh.ru. Если вакансия на hh.ru впоследствии изменится или уйдёт в
  архив — уже созданная сессия и её данные не меняются.
- **Контекст оценки и отчёта для сессий по вакансии.** LLM-оценка ответа (`evaluate=true`)
  и итоговый отчёт (`finish`) для таких сессий используют вместо профессии и уровня
  название вакансии и требуемый опыт из снимка; если опыт в вакансии не указан, в LLM
  передаётся значение «не указан».
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
| `400` | невалидное тело запроса: нарушены ограничения полей (`totalQuestions` вне 10..20, пустой `answerText`, отсутствующий enum, `vacancyText` короче 50 или длиннее 20000 символов), не выполнен XOR `vacancyUrl`/`vacancyText` в `by-vacancy`, `vacancyUrl` не является ссылкой на вакансию hh.ru, битый JSON, неверный тип path/query-параметра |
| `401` | нет валидного access-токена (ставит Security-фильтр, тело пустое) |
| `403` | вопрос или сессия принадлежат другому пользователю |
| `404` | сессия или вопрос не найдены (в т.ч. чужие); `continue` при отсутствии неотвеченных вопросов; вакансия по `vacancyUrl` не найдена на hh.ru или архивирована |
| `409` | вопрос уже отвечен либо не принадлежит указанной в пути сессии |
| `500` | банк вопросов не вернул вопросов при создании каталожной сессии |
| `503` | недоступен LLM-сервис (при оценке ответа с `evaluate=true`, формировании отчёта в `finish`, генерации вопросов в `by-vacancy` или если LLM вернула пустой список вопросов); недоступен hh.ru при получении вакансии по `vacancyUrl` |

`503` отдаётся глобальным обработчиком одного из двух исключений: `LlmException`
(`AI service unavailable.`) — на ручках, дергающих LLM (`POST .../questions/{questionId}?evaluate=true`,
`POST .../finish`, `POST /sessions/by-vacancy`), и `VacancyFetchException`
(`Vacancy service unavailable.`) — когда hh.ru недоступен по сети при получении вакансии
по ссылке.
