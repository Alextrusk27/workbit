import { useMutation, useQuery } from '@tanstack/react-query'
import { billingApi } from './api'

export const billingKeys = {
  quota: ['billing', 'quota'] as const,
  usage: ['billing', 'usage'] as const,
  payment: (id: string) => ['billing', 'payment', id] as const,
}

export const PAYMENT_ID_KEY = 'workbit:payment-id'

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

export function useCreatePayment() {
  return useMutation({ mutationFn: billingApi.createPayment })
}

const PAYMENT_POLL_INTERVAL_MS = 2000
const PAYMENT_POLL_MAX_ATTEMPTS = 150

export function usePayment(id: string | null) {
  return useQuery({
    queryKey: billingKeys.payment(id ?? ''),
    queryFn: () => billingApi.payment(id!),
    enabled: !!id,
    refetchInterval: (query) => {
      if (query.state.data && query.state.data.status !== 'PENDING')
        return false
      if (query.state.dataUpdateCount >= PAYMENT_POLL_MAX_ATTEMPTS) return false
      return PAYMENT_POLL_INTERVAL_MS
    },
  })
}
