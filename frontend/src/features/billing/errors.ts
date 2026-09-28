import { ApiRequestError } from '@/lib/api'

export function isNotEnoughLimits(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 402
}
