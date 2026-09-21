import { apiFetch } from '@/lib/api'

export interface Balance {
  limits: number
  expiresAt: string | null
  paid: boolean
}

export type UsageEventKind = 'SPEND' | 'CREDIT'
export type UsageOperation =
  | 'INTERVIEW'
  | 'TRAINING'
  | 'TRAINING_RESTART'
  | 'TRAINING_MORE'
  | 'REFERENCE_ANSWER'
  | 'TOPUP'
  | 'WELCOME'
  | 'EXPIRE'

export interface UsageEvent {
  at: string
  kind: UsageEventKind
  operation: UsageOperation
  delta: number
  label: string
}

export interface Usage extends Balance {
  events: UsageEvent[]
}

export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED'

export interface PaymentCreated {
  paymentId: string
  paymentUrl: string
}

export interface Payment {
  status: PaymentStatus
  limits: number
  amount: number
}

export const billingApi = {
  balance: () => apiFetch<Balance>('/billing/quota'),
  usage: () => apiFetch<Usage>('/billing/usage'),
  createPayment: (limits: number) =>
    apiFetch<PaymentCreated>('/billing/payments', {
      method: 'POST',
      body: { limits },
    }),
  payment: (id: string) => apiFetch<Payment>(`/billing/payments/${id}`),
}
