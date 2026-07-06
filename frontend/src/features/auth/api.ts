import { apiFetch } from '@/lib/api'

/** Профиль от `GET /auth/me`. Поля «план»/подписки в домене нет. */
export interface UserResponse {
  email: string
  created: string
}

export interface Credentials {
  email: string
  password: string
}

export const authApi = {
  register: (data: Credentials) =>
    apiFetch<void>('/auth/register', { method: 'POST', body: data }),

  verifyEmail: (token: string) =>
    apiFetch<void>('/auth/verify-email', { method: 'POST', body: { token } }),

  resendVerification: (email: string) =>
    apiFetch<void>('/auth/resend-verification', {
      method: 'POST',
      body: { email },
    }),

  login: (data: Credentials) =>
    apiFetch<void>('/auth/login', { method: 'POST', body: data }),

  me: () => apiFetch<UserResponse>('/auth/me'),

  logout: () => apiFetch<void>('/auth/logout', { method: 'POST' }),

  forgotPassword: (email: string) =>
    apiFetch<void>('/auth/forgot-password', {
      method: 'POST',
      body: { email },
    }),

  resetPassword: (token: string, newPassword: string) =>
    apiFetch<void>('/auth/reset-password', {
      method: 'POST',
      body: { token, newPassword },
    }),

  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    apiFetch<void>('/auth/change-password', { method: 'PATCH', body: data }),

  deleteAccount: () => apiFetch<void>('/auth/delete', { method: 'DELETE' }),
}
