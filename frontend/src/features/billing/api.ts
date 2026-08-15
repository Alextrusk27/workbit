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

export type PaymentProduct = 'PLAN_PRO' | 'PLAN_MAX'
export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED'

export interface PaymentCreated {
  paymentId: string
  paymentUrl: string
}

export interface Payment {
  status: PaymentStatus
  product: PaymentProduct
}

export const billingApi = {
  quota: () => apiFetch<Quota>('/billing/quota'),
  usage: () => apiFetch<Usage>('/billing/usage'),
  createPayment: (product: PaymentProduct) =>
    apiFetch<PaymentCreated>('/billing/payments', {
      method: 'POST',
      body: { product },
    }),
  payment: (id: string) => apiFetch<Payment>(`/billing/payments/${id}`),
}
