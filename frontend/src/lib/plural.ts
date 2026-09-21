/** «1 вопрос», «3 вопроса», «5 вопросов», «21 вопрос». */
export function questionsWord(count: number): string {
  const mod100 = count % 100
  if (mod100 >= 11 && mod100 <= 14) return 'вопросов'
  const mod10 = count % 10
  if (mod10 === 1) return 'вопрос'
  return mod10 >= 2 && mod10 <= 4 ? 'вопроса' : 'вопросов'
}

/** Родительный падеж после «по итогам»: «1 ответа», «5 ответов». */
export function answersWord(count: number): string {
  const mod100 = count % 100
  if (mod100 >= 11 && mod100 <= 14) return 'ответов'
  return count % 10 === 1 ? 'ответа' : 'ответов'
}

/** «1 лимит», «3 лимита», «5 лимитов», «21 лимит». */
export function limitsWord(count: number): string {
  const mod100 = count % 100
  if (mod100 >= 11 && mod100 <= 14) return 'лимитов'
  const mod10 = count % 10
  if (mod10 === 1) return 'лимит'
  return mod10 >= 2 && mod10 <= 4 ? 'лимита' : 'лимитов'
}
