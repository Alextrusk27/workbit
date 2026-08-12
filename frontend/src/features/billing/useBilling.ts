import { useQuery } from '@tanstack/react-query'
import { billingApi } from './api'

export const billingKeys = {
  quota: ['billing', 'quota'] as const,
}

export function useQuota() {
  return useQuery({
    queryKey: billingKeys.quota,
    queryFn: billingApi.quota,
    staleTime: 60 * 1000,
  })
}
