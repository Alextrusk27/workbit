import { apiFetch } from '@/lib/api'

/** Профиль от `GET /auth/me`. Поля «план»/подписки в домене нет. */
export interface UserResponse {
  email: string
  created: string
}

export const authApi = {
  requestCode: (email: string) =>
    apiFetch<void>('/auth/request-code', { method: 'POST', body: { email } }),

  verifyCode: (email: string, code: string) =>
    apiFetch<void>('/auth/verify-code', {
      method: 'POST',
      body: { email, code },
    }),

  me: () => apiFetch<UserResponse>('/auth/me'),

  logout: () => apiFetch<void>('/auth/logout', { method: 'POST' }),

  deleteAccount: () => apiFetch<void>('/auth/delete', { method: 'DELETE' }),
}
