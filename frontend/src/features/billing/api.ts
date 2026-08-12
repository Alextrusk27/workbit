import { apiFetch } from '@/lib/api'

export type Plan = 'FREE' | 'PRO' | 'MAX'

export interface Quota {
  plan: Plan
  planExpiresAt: string | null
  planInterviewsLeft: number
  planTrainingsLeft: number
  packInterviewsLeft: number
  packTrainingsLeft: number
}

export const billingApi = {
  quota: () => apiFetch<Quota>('/billing/quota'),
}
