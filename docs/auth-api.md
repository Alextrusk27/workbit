# Auth API — `/api/v1/auth`

Аутентификация по email/паролю с двухтокенной схемой (access + refresh).
Все запросы и ответы — JSON.

## Аутентификация

- **access** — короткоживущий JWT (минуты). Передаётся в каждом защищённом запросе:
  `Authorization: Bearer <accessToken>`.
- **refresh** — долгоживущий токен (дни). Хранится у клиента, передаётся только в теле на
  `/refresh` и `/logout`.

Рекомендуемый клиентский флоу: при `401` на защищённой ручке вызвать `/refresh`, получить новую
пару токенов и повторить запрос. Refresh **одноразовый** — после `/refresh` старое значение
становится недействительным; в ответе всегда приходит новый refresh.

## Эндпоинты

| Метод | Путь | Назначение | Авторизация |
|---|---|---|---|
| POST | `/register` | регистрация, отправка письма подтверждения | — |
| POST | `/verify-email` | подтверждение email по токену из письма | — |
| POST | `/resend-verification` | повторная отправка письма подтверждения | — |
| POST | `/login` | вход | — |
| POST | `/refresh` | обмен refresh на новую пару токенов | — |
| POST | `/logout` | отзыв refresh-токена | — |
| PATCH | `/change-password` | смена пароля | Bearer |
| POST | `/forgot-password` | запрос ссылки сброса пароля | — |
| POST | `/reset-password` | установка нового пароля по токену из письма | — |

## Запросы (DTO)

```java
record RegistrationRequest(@Email String email, @Size(min = 8) String password)
record LoginRequest(@Email String email, @Size(min = 8) String password)

record VerifyEmailRequest(String token)
record ResendVerificationRequest(@Email String email)

record RefreshRequest(String refreshToken)
record LogoutRequest(String refreshToken)

record ChangePasswordRequest(String oldPassword, @Size(min = 8) String newPassword)
record ForgotPasswordRequest(@Email String email)
record ResetPasswordRequest(String token, @Size(min = 8) String newPassword)
```

Все строковые поля обязательны и непустые. `password` / `newPassword` — минимум 8 символов.

## Ответы

```java
record TokenResponse(String accessToken, String refreshToken)
```

`TokenResponse` возвращают `/login`, `/refresh`, `/verify-email`.
`/register`, `/resend-verification`, `/forgot-password`, `/reset-password`, `/logout`,
`/change-password` возвращают пустое тело со статусом `200`.

## Поведение

- **Вход возможен только после подтверждения email.** До подтверждения `/login` отвечает `401`.
  `/register` письмо отправляет, но токены не выдаёт; токены приходят в ответе `/verify-email`.
- **`/refresh`**: предъявление уже использованного (отозванного) refresh-токена трактуется как
  компрометация — отзываются все активные сессии пользователя, ответ `401`.
- **`/reset-password`** и смена пароля инвалидируют сессии: после успешного сброса все
  refresh-токены пользователя отзываются.
- **`/forgot-password`** и **`/resend-verification`** всегда возвращают `200` независимо от того,
  существует ли пользователь с таким email (не раскрывают наличие учётной записи).

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
