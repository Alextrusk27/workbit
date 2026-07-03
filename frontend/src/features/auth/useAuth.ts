import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiRequestError } from '@/lib/api'
import { authApi, type Credentials, type UserResponse } from './api'

const ME_KEY = ['me'] as const

/** Запрос профиля: 401 (не залогинен) — это `null`, а не ошибка загрузки. */
async function fetchMe(): Promise<UserResponse | null> {
  try {
    return await authApi.me()
  } catch (e) {
    if (e instanceof ApiRequestError && e.status === 401) return null
    throw e
  }
}

/** Принудительно перечитать профиль в кэш (staleTime:0 обходит кэш). Дожидаемся
 *  этого перед навигацией после логина — иначе `RequireAuth` увидит старый `['me']`. */
function refreshMe(qc: ReturnType<typeof useQueryClient>) {
  return qc.fetchQuery({ queryKey: ME_KEY, queryFn: fetchMe, staleTime: 0 })
}

export function useAuth() {
  const query = useQuery<UserResponse | null>({
    queryKey: ME_KEY,
    queryFn: fetchMe,
    retry: false,
    staleTime: 60_000,
  })

  return {
    user: query.data ?? null,
    isAuthenticated: !!query.data,
    isLoading: query.isLoading,
  }
}

export function useLogin() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: Credentials) => authApi.login(data),
    onSuccess: () => refreshMe(qc),
  })
}

export function useRegister() {
  return useMutation({
    mutationFn: (data: Credentials) => authApi.register(data),
  })
}

export function useVerifyEmail() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (token: string) => authApi.verifyEmail(token),
    onSuccess: () => refreshMe(qc),
  })
}

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => authApi.logout(),
    onSettled: () => qc.setQueryData(ME_KEY, null),
  })
}

export function useForgotPassword() {
  return useMutation({
    mutationFn: (email: string) => authApi.forgotPassword(email),
  })
}

export function useResetPassword() {
  return useMutation({
    mutationFn: (vars: { token: string; newPassword: string }) =>
      authApi.resetPassword(vars.token, vars.newPassword),
  })
}
