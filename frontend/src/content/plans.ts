export interface Plan {
  name: string
  price: string
  note: string
  cta: string
  featured: boolean
  /** Полный список — страница тарифов. */
  features: string[]
  /** Короткий список — превью на главной. */
  previewFeatures: string[]
}

export const plans: Plan[] = [
  {
    name: 'Бесплатно',
    price: '0 ₽',
    note: 'Для знакомства',
    cta: 'Начать бесплатно',
    featured: false,
    features: [
      '3 интервью в месяц',
      'Профессии: Java, Python, тестирование',
      'Балл за каждый ответ',
      'Итоговый фидбэк по сессии',
    ],
    previewFeatures: [
      '3 интервью в месяц',
      'Балл за каждый ответ',
      'Итоговый фидбэк',
    ],
  },
  {
    name: 'Про',
    price: '490 ₽',
    note: 'в месяц',
    cta: 'Перейти на Про',
    featured: true,
    features: [
      'Безлимит интервью',
      'Разбор с правками на полях',
      'Вероятность оффера',
      'История и продолжение сессий',
      'Все уровни: от Junior до Lead',
    ],
    previewFeatures: [
      'Безлимит интервью',
      'Разбор с правками на полях',
      'Вероятность оффера',
      'История сессий',
    ],
  },
]
