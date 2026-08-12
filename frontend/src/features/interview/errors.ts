import { ApiRequestError, getErrorMessage } from '@/lib/api'

/** Русское сообщение об ошибке создания интервью. Бэк отдаёт текст по-английски,
 *  поэтому маппим по HTTP-статусу: невалидная ссылка отсекается ещё превью, до
 *  create такие ошибки доходят редко. */
export function interviewCreateErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 404)
      return 'Вакансия не найдена или снята с публикации. Проверьте ссылку.'
    if (error.status === 400)
      return 'Ссылка не похожа на вакансию hh.ru. Проверьте адрес.'
    if (error.status === 409)
      return 'По этой вакансии уже есть незавершённое интервью. Завершите его, прежде чем начинать новое.'
    if (error.status === 402)
      return 'Интервью на вашем тарифе закончились. Обновите тариф или докупите пакет на странице тарифов.'
    if (error.status === 503)
      return 'Сервис временно недоступен — попробуйте ещё раз чуть позже.'
  }
  return getErrorMessage(error)
}
