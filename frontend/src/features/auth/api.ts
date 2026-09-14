import { apiFetch } from '@/lib/api'

/** Профиль от `GET /auth/me`. Поля «план»/подписки в домене нет. */
export interface UserResponse {
  email: string
  created: string
}

/** Ответ `POST /auth/verify-code`: `newUser` — первая авторизация (регистрация). */
export interface VerifyCodeResponse {
  newUser: boolean
}

export const authApi = {
  requestCode: (
    email: string,
    personalDataConsent: boolean,
    captchaToken?: string,
  ) =>
    apiFetch<void>('/auth/request-code', {
      method: 'POST',
      body: { email, personalDataConsent, captchaToken },
    }),

  verifyCode: (email: string, code: string) =>
    apiFetch<VerifyCodeResponse>('/auth/verify-code', {
      method: 'POST',
      body: { email, code },
    }),

  me: () => apiFetch<UserResponse>('/auth/me'),

  logout: () => apiFetch<void>('/auth/logout', { method: 'POST' }),

  deleteAccount: () => apiFetch<void>('/auth/delete', { method: 'DELETE' }),
}
