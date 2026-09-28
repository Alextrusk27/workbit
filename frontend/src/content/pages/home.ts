import { WELCOME_LIMITS } from '@/content/limits'
import type { Cta, Hero, PageSeo } from '@/content/types'

export type HomeIcon = 'role' | 'pencil' | 'chart' | 'clock'

export interface HomeContent {
  hero: Hero & { text: string; cta: Cta }
  simulator: {
    title: string
    lead: string
    vacancy: { title: string; body: string; note: string }
    scoring: {
      title: string
      body: string
      rows: { title: string; score: number }[]
    }
    features: { icon: HomeIcon; title: string; body: string }[]
    professions: { label: string; items: string[]; tail: string }
  }
  demo: {
    title: string
    lead: string
    question: { title: string; who: string; text: string }
    answer: { title: string; who: string; text: string }
    review: { title: string; who: string; score: number; text: string }
    progress: {
      title: string
      lead: string
      best: { value: string; stars: number; label: string; shortLabel: string }
      trend: { value: string; label: string }
      offer: { value: string; label: string }
      points: { x: number; y: number; date: string }[]
      note: string
    }
  }
  trainer: {
    title: string
    body: string
    cta: Cta
    steps: { label: string; value: string }[]
  }
  faq: { title: string; lead: string }
  cta: {
    title: string
    body: string
    primary: Cta
    secondary: Cta
    note: string
  }
}

export const seo: PageSeo = {
  title: 'Тренажёр собеседований с ИИ: бесплатный старт | Workbit',
  description:
    'Тренажёр собеседований с ИИ, симулятор реального интервью: пробное собеседование по вакансии с hh.ru и тренировка навыка. Разбор ответов и оценка шансов на оффер.',
}

export const home: HomeContent = {
  hero: {
    lead: 'Тренажёр собеседований',
    accent: 'с ИИ-интервьюером',
    text: 'Готовься к собеседованию онлайн по вакансии с hh.ru. Вопросы подберём по требованиям работодателя, каждый ответ оценим и разберём.',
    cta: { label: 'Начать интервью бесплатно', start: true },
  },
  simulator: {
    title: 'Симулятор собеседования',
    lead: 'Пройди пробное собеседование по любой вакансии с hh.ru.',
    vacancy: {
      title: 'Интервью по вакансии',
      body: 'Вставь ссылку на вакансию с hh.ru, и тренажёр соберёт сессию под её требования.',
      note: 'Первое интервью бесплатно · [Как устроено ИИ-интервью →](/ai-interview)',
    },
    scoring: {
      title: 'Оценка каждого ответа',
      body: 'Звёзды от 1 до 5 за каждый ответ. В отчёте видно, где просел и что подтянуть.',
      rows: [
        { title: 'Вопрос 4 · Воронка и метрики', score: 5 },
        { title: 'Вопрос 5 · Падение CTR', score: 2 },
        { title: 'Вопрос 6 · Сегментация', score: 4 },
      ],
    },
    features: [
      {
        icon: 'role',
        title: 'Вопросы под вакансию',
        body: 'Вопросы собираются из текста вакансии.',
      },
      {
        icon: 'pencil',
        title: 'Правки на полях',
        body: 'В отчёте у каждого ответа есть пометки, что получилось и чего не хватило.',
      },
      {
        icon: 'chart',
        title: 'Вероятность оффера',
        body: 'По итогам интервью покажем шансы на оффер: низкие, средние или высокие.',
      },
      {
        icon: 'clock',
        title: 'История сессий',
        body: 'Все попытки по вакансии сохраняются в личном кабинете.',
      },
    ],
    professions: {
      label: 'Подойдёт для любой профессии:',
      items: [
        'Маркетинг',
        'Разработка',
        'Продажи',
        'Аналитика',
        'Финансы',
        'HR',
        'Дизайн',
        'Поддержка',
      ],
      tail: 'и другие',
    },
  },
  demo: {
    title: 'Тестовое собеседование',
    lead: 'Отвечай интервьюеру своими словами, текстом или голосом.',
    question: {
      title: 'Вопрос',
      who: 'Workbit-интервьюер · 7 / 10 · Маркетолог',
      text: 'CTR рекламной кампании упал вдвое при том же бюджете. Как будешь искать причину?',
    },
    answer: {
      title: 'Ответ',
      who: 'Ты',
      text: 'Сначала посмотрю частоту показов и выгорание креативов, потом разбивку по площадкам и сегментам. Если просело везде равномерно, обновлю креативы и пересоберу аудитории.',
    },
    review: {
      title: 'Разбор',
      who: 'Разбор рецензента',
      score: 4,
      text: 'Верная логика: частота → площадки → сегменты. Уточни, как отделишь выгорание креатива от выгорания аудитории. Интервьюеры спросят про тест.',
    },
    progress: {
      title: 'Прогресс по вакансии',
      lead: 'Интернет-маркетолог · оценка за попытку, 5 последних интервью',
      best: {
        value: '4,2',
        stars: 4,
        label: 'Лучшая оценка',
        shortLabel: 'Лучшая',
      },
      trend: { value: '+1,2', label: 'Динамика' },
      offer: { value: 'высокая', label: 'Оффер' },
      points: [
        { x: 6, y: 92.5, date: '12 мая' },
        { x: 28, y: 81.3, date: '18 мая' },
        { x: 50, y: 85, date: '26 мая' },
        { x: 72, y: 62.5, date: '2 июн' },
        { x: 94, y: 40, date: '9 июн' },
      ],
      note: 'Проходи интервью по вакансии повторно, прогресс будет виден в её карточке.',
    },
  },
  trainer: {
    title: 'Тренажёр навыков',
    body: 'Короткие тренировки по одному навыку между интервью. Выбери навык, профессию и уровень.',
    cta: { label: 'Попробовать тренажёр', to: '/skills-trainer' },
    steps: [
      { label: 'Навык', value: 'Работа с возражениями' },
      { label: 'Профессия', value: 'Менеджер по продажам' },
      { label: 'Уровень', value: 'Средний' },
    ],
  },
  faq: {
    title: 'Частые вопросы',
    lead: 'Коротко о формате, оценке ответов и оплате. [Все вопросы →](/faq)',
  },
  cta: {
    title: 'Пройди тестовое собеседование',
    body: 'Хватит ссылки на вакансию с hh.ru. Вопросы будут готовы через несколько секунд.',
    primary: { label: 'Пройти первое интервью бесплатно', start: true },
    secondary: { label: 'Попробовать тренажёр навыков', to: '/skills-trainer' },
    note: `${WELCOME_LIMITS} лимитов на старте, вход по email, без анкеты и без карты`,
  },
}
