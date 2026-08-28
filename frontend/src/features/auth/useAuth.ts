import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiRequestError } from '@/lib/api'
import { authApi, type UserResponse } from './api'
import { getCaptchaToken } from './captcha'

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
    retry: 1,
    staleTime: 60_000,
  })

  return {
    user: query.data ?? null,
    isAuthenticated: !!query.data,
    isLoading: query.isLoading,
    isError: query.isError,
    refetch: query.refetch,
  }
}

/** Токен капчи запрашивается внутри мутации: `isPending` накрывает и возможное
 *  задание капчи, и HTTP; ресенд в CodeForm получает капчу той же дорогой. */
export function useRequestCode() {
  return useMutation({
    mutationFn: async (vars: {
      email: string
      personalDataConsent: boolean
    }) => {
      const captchaToken = await getCaptchaToken()
      return authApi.requestCode(
        vars.email,
        vars.personalDataConsent,
        captchaToken,
      )
    },
  })
}

export function useVerifyCode() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (vars: { email: string; code: string }) =>
      authApi.verifyCode(vars.email, vars.code),
    onSuccess: () =>
      refreshMe(qc).catch(() => {
        void qc.invalidateQueries({ queryKey: ME_KEY })
      }),
  })
}

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => authApi.logout(),
    onSettled: () => qc.setQueryData(ME_KEY, null),
  })
}

/** Удаление аккаунта: бэк чистит куки, сбрасываем `['me']`. Навигацию прочь с
 *  защищённого роута делает страница (иначе `RequireAuth` успеет кинуть на /login). */
export function useDeleteAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => authApi.deleteAccount(),
    onSuccess: () => qc.setQueryData(ME_KEY, null),
  })
}
