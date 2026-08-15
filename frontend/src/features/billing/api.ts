import { apiFetch } from '@/lib/api'

export type Plan = 'FREE' | 'PRO' | 'MAX'

export interface Quota {
  plan: Plan
  planExpiresAt: string | null
  planInterviewsLeft: number
  planTrainingsLeft: number
}

export interface UsageCounter {
  left: number
  total: number
}

export type UsageEventKind = 'SPEND' | 'CREDIT'
export type UsageTarget = 'INTERVIEW' | 'TRAINING'

export interface UsageEvent {
  at: string
  kind: UsageEventKind
  target: UsageTarget
  delta: number
  label: string
}

export interface Usage {
  interviews: UsageCounter
  trainings: UsageCounter
  events: UsageEvent[]
}

export const billingApi = {
  quota: () => apiFetch<Quota>('/billing/quota'),
  usage: () => apiFetch<Usage>('/billing/usage'),
}
