export type SttEventType = 'PARTIAL' | 'FINAL' | 'REFINEMENT' | 'ERROR'

export interface SttEvent {
  type: SttEventType
  text: string
}

export const STOP_COMMAND = 'stop'

const BASE_URL: string =
  import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1'

export function dictationUrl(): string {
  return `${BASE_URL.replace(/^http/, 'ws')}/speech/stt`
}
