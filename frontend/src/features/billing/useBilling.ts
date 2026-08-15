import { useQuery } from '@tanstack/react-query'
import { billingApi } from './api'

export const billingKeys = {
  quota: ['billing', 'quota'] as const,
  usage: ['billing', 'usage'] as const,
}

export function useQuota() {
  return useQuery({
    queryKey: billingKeys.quota,
    queryFn: billingApi.quota,
    staleTime: 60 * 1000,
  })
}

export function useUsage() {
  return useQuery({
    queryKey: billingKeys.usage,
    queryFn: billingApi.usage,
  })
}
