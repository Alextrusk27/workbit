# Auth API — `/api/v1/auth`

Аутентификация по email/паролю с двухтокенной схемой (access + refresh).
Оба токена доставляются в **HttpOnly-cookie** — клиентский JS их не видит и не хранит.
Все тела запросов и ответов — JSON.

## Аутентификация

Токены выдаются заголовками `Set-Cookie` (не в теле ответа) и приходят обратно автоматически.
Браузер-клиент должен слать запросы с `credentials: 'include'`.

- **access** — короткоживущий JWT (минуты). Cookie `access_token`
  (`Path=/`, `HttpOnly`, `Secure`, `SameSite=Lax`). Уходит с каждым запросом к API;
  по нему аутентифицируются защищённые ручки. Fallback `Authorization: Bearer <accessToken>`
  поддержан для Swagger в dev.
- **refresh** — долгоживущий токен (30 дней). Cookie `refresh_token`
  (`Path=/api/v1/auth`, `HttpOnly`, `Secure`, `SameSite=Lax`, `Max-Age=30д`).
  Уходит только на `/refresh` и `/logout`.

Рекомендуемый клиентский флоу: при `401` на защищённой ручке вызвать `/refresh`, получить новую
пару cookie и повторить запрос. Refresh **одноразовый** — после `/refresh` старое значение
становится недействительным; в ответе всегда приходит новый refresh (обе cookie перезаписываются).

## Эндпоинты

| Метод | Путь | Назначение | Авторизация |
|---|---|---|---|
| POST | `/register` | регистрация, отправка письма подтверждения | — |
| POST | `/verify-email` | подтверждение email по токену из письма | — |
| POST | `/resend-verification` | повторная отправка письма подтверждения | — |
| POST | `/login` | вход | — |
| GET | `/me` | профиль текущего пользователя | cookie `access_token` |
| POST | `/refresh` | обмен refresh-cookie на новую пару токенов | cookie `refresh_token` |
| POST | `/logout` | отзыв refresh-токена и очистка cookie | cookie `refresh_token` |
| PATCH | `/change-password` | смена пароля | cookie `access_token` |
| POST | `/forgot-password` | запрос ссылки сброса пароля | — |
| POST | `/reset-password` | установка нового пароля по токену из письма | — |
| DELETE | `/delete` | удаление аккаунта | cookie `access_token` |

## Запросы (DTO)

```java
record RegistrationRequest(@Email String email, @Size(min = 8) String password)
record LoginRequest(@Email String email, @Size(min = 8) String password)

record VerifyEmailRequest(String token)
record ResendVerificationRequest(@Email String email)

record ChangePasswordRequest(String oldPassword, @Size(min = 8) String newPassword)
record ForgotPasswordRequest(@Email String email)
record ResetPasswordRequest(String token, @Size(min = 8) String newPassword)
```

Все строковые поля обязательны и непустые. `password` / `newPassword` — минимум 8 символов.

`/refresh` и `/logout` тела не принимают — refresh-токен берётся из cookie `refresh_token`.

## Ответы

`/me` возвращает `200` с телом:

```java
record UserResponse(String email, Instant created)
```

У остальных эндпоинтов тело ответа пустое; токены передаются в заголовках `Set-Cookie`.

- `/login`, `/verify-email`, `/refresh` — статус `200`, ставят cookie `access_token` и `refresh_token`.
- `/register`, `/resend-verification`, `/forgot-password`, `/reset-password`, `/change-password` —
  пустое тело со статусом `200`.
- `/logout`, `/delete` — статус `204`, гасят обе cookie (`Set-Cookie` с `Max-Age=0`).

## Поведение

- **Вход возможен только после подтверждения email.** До подтверждения `/login` отвечает `401`.
  `/register` письмо отправляет, но токены не выдаёт; токены приходят в ответе `/verify-email`.
- **`/refresh`**: предъявление уже использованного (отозванного) refresh-токена трактуется как
  компрометация — отзываются все активные сессии пользователя, ответ `401`.
- **`/reset-password`** и смена пароля инвалидируют сессии: после успешного сброса все
  refresh-токены пользователя отзываются.
- **`/forgot-password`** и **`/resend-verification`** всегда возвращают `200` независимо от того,
  существует ли пользователь с таким email (не раскрывают наличие учётной записи).
- **`/delete`** — физическое удаление: запись пользователя удаляется из БД, вместе с ней
  каскадами сносятся все refresh-токены, сессии интервью с вопросами и ответами, фидбэк
  и отчёты. Восстановление невозможно, состояния «деактивирован» у аккаунта нет.
  Доступ отзывается сразу: принципал резолвится из БД на каждый запрос, поэтому ещё не
  истёкший access-токен перестаёт работать немедленно. Email освобождается — зарегистрироваться
  с ним можно снова, но это будет новая учётная запись, никак не связанная с прежней.
- **Автоудаление неактивных аккаунтов.** Отметка активности (`last_seen`) обновляется каждый раз,
  когда выдаётся пара токенов, — на `/login`, `/verify-email` и `/refresh`. Ежедневная фоновая
  задача (в 3 часа ночи по времени сервера) находит аккаунты без активности 335 дней, шлёт на их email письмо-предупреждение
  и запоминает дату отправки; если за следующие 30 дней вход так и не состоялся, аккаунт удаляется
  физически — тем же способом, что и по `/delete`. Любая выдача токенов до истечения этих 30 дней
  снимает отметку предупреждения и отменяет удаление. Отдельного эндпоинта у этого механизма нет;
  для клиента он выглядит как исчезнувшая учётная запись: `/login` начнёт отвечать `401`.
- **Лимит частоты**: `/register`, `/forgot-password` и `/resend-verification` ограничены
  по IP — не более 5 запросов за 15 минут на эндпоинт (настраивается через
  `RATE_LIMIT_LIMIT` / `RATE_LIMIT_WINDOW`). При превышении — `429`.

## Ошибки

Тело ошибки:

```json
{
  "timestamp": "2026-06-16T12:00:00",
  "status": "UNAUTHORIZED",
  "message": "Bad credentials",
  "errors": ["Invalid credentials"]
}
```

| Код | Когда |
|---|---|
| `400` | невалидное тело запроса (нарушение ограничений полей, битый JSON) |
| `401` | неверные учётные данные; email не подтверждён; email уже используется; refresh/verify/reset-токен недействителен, истёк или уже использован |
| `404` | запрашиваемый объект не найден |
| `429` | превышен лимит частоты запросов с одного IP (`/register`, `/forgot-password`, `/resend-verification`) |
