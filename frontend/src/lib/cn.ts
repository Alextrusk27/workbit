export type ClassValue = string | false | null | undefined

/** Собирает className из строк, отбрасывая ложные значения. */
export function cn(...values: ClassValue[]): string {
  return values.filter(Boolean).join(' ')
}
