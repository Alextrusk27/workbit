import { ApiRequestError, getErrorMessage } from '@/lib/api'

/** Русское сообщение об ошибке создания интервью. Бэк отдаёт текст по-английски,
 *  поэтому маппим по HTTP-статусу: невалидная ссылка отсекается ещё превью, до
 *  create такие ошибки доходят редко. */
export function interviewCreateErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 404)
      return 'Вакансия не найдена или снята с публикации. Проверь ссылку.'
    if (error.status === 400)
      return 'Ссылка не похожа на вакансию hh.ru. Проверь адрес.'
    if (error.status === 409)
      return 'По этой вакансии уже есть незавершённое интервью. Заверши его, прежде чем начинать новое.'
    if (error.status === 402)
      return 'Интервью на твоём тарифе закончились. Обнови или продли тариф на странице тарифов.'
    if (error.status === 503)
      return 'Сервис временно недоступен — попробуй ещё раз чуть позже.'
  }
  return getErrorMessage(error)
}
