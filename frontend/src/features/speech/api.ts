import { BASE_URL } from '@/lib/api'

export type SttEventType = 'PARTIAL' | 'FINAL' | 'REFINEMENT' | 'ERROR'

export interface SttEvent {
  type: SttEventType
  text: string
}

export const STOP_COMMAND = 'stop'

export function dictationUrl(): string {
  return `${BASE_URL.replace(/^http/, 'ws')}/speech/stt`
}
