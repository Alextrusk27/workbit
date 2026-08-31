import { faq } from '@/content/faq'
import { plans } from '@/content/plans'

export const SITE = 'https://workbit.ru'

export interface SeoPage {
  path: string
  title: string
  description: string
  sources: string[]
  jsonLd?: () => object[]
}

function price(value: string): string {
  const match = /^(\d+) ₽$/.exec(value)
  if (!match) throw new Error(`Unexpected plan price format: ${value}`)
  return match[1]
}

export const seoPages: SeoPage[] = [
  {
    path: '/',
    title: 'Тренажёр собеседований с AI: бесплатный старт | Workbit',
    description:
      'Тренажёр собеседований с AI — симулятор реального интервью: пробное собеседование по вакансии с hh.ru и тренировка навыка. Разбор ответов и честная оценка шансов на оффер.',
    sources: ['frontend/src/pages/HomePage.tsx', 'frontend/src/content/faq.ts'],
    jsonLd: () => [
      {
        '@context': 'https://schema.org',
        '@type': 'Organization',
        name: 'Workbit',
        url: SITE,
        logo: `${SITE}/favicon.png`,
        email: 'support@workbit.ru',
      },
      {
        '@context': 'https://schema.org',
        '@type': 'WebSite',
        name: 'Workbit',
        url: SITE,
      },
    ],
  },
  {
    path: '/ai-interview',
    title: 'Собеседование с нейросетью по вакансии с hh.ru | Workbit',
    description:
      'Пробное мок-интервью по ссылке с hh.ru: полная сессия вопросов под требования вакансии, ответы текстом или голосом, фидбек с оценками в конце.',
    sources: ['frontend/src/pages/AiInterviewPage.tsx'],
  },
  {
    path: '/skills-trainer',
    title: 'Тренажёр с вопросами и ответами ИИ для собеседования | Workbit',
    description:
      'Вопросы на собеседовании с ответами ИИ: десять вопросов по навыку — от разработки до бухгалтерии и права. Подготовка к техническому собеседованию и не только.',
    sources: ['frontend/src/pages/SkillsTrainerPage.tsx'],
  },
  {
    path: '/faq',
    title: 'Частые вопросы о тренажёре собеседований | Workbit',
    description:
      'Как работает AI-интервью, по каким профессиям есть вопросы, как ИИ оценивает ответы и что входит в тарифы — короткие ответы на частые вопросы.',
    sources: ['frontend/src/pages/FaqPage.tsx', 'frontend/src/content/faq.ts'],
    jsonLd: () => [
      {
        '@context': 'https://schema.org',
        '@type': 'FAQPage',
        mainEntity: faq.map((item) => ({
          '@type': 'Question',
          name: item.q,
          acceptedAnswer: { '@type': 'Answer', text: item.a },
        })),
      },
    ],
  },
  {
    path: '/pricing',
    title: 'Сколько стоит подготовка к собеседованию | Workbit',
    description: `${plans
      .map((p) => `${p.name} — ${p.period ? `${p.price} в месяц` : p.price}`)
      .join(
        ', ',
      )} — тарифы тренажёра собеседований. Разовый платёж на 30 дней, без автосписаний и привязки карты.`,
    sources: [
      'frontend/src/pages/PricingPage.tsx',
      'frontend/src/content/plans.ts',
    ],
    jsonLd: () => [
      {
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        name: 'Workbit',
        url: SITE,
        applicationCategory: 'EducationalApplication',
        operatingSystem: 'Web',
        offers: plans.map((p) => ({
          '@type': 'Offer',
          name: `Тариф «${p.name}»`,
          price: price(p.price),
          priceCurrency: 'RUB',
          url: `${SITE}/pricing`,
        })),
      },
    ],
  },
  {
    path: '/privacy',
    title: 'Политика конфиденциальности | Workbit',
    description:
      'Политика конфиденциальности и обработки персональных данных сервиса Workbit.',
    sources: ['docs/privacy-policy.md'],
  },
  {
    path: '/user-agreement',
    title: 'Пользовательское соглашение | Workbit',
    description:
      'Пользовательское соглашение сервиса Workbit: условия использования тренажёра собеседований.',
    sources: ['docs/user-agreement.md'],
  },
  {
    path: '/offer',
    title: 'Публичная оферта | Workbit',
    description: 'Публичная оферта сервиса Workbit: условия оплаты тарифов.',
    sources: ['docs/offer.md'],
  },
]

export const notFoundSeo = {
  title: 'Страница не найдена | Workbit',
  description: 'Такой страницы нет или она ещё не готова.',
}

export function seoFor(pathname: string): {
  title: string
  description: string
  canonical: string | null
} {
  const path = pathname.length > 1 ? pathname.replace(/\/+$/, '') : '/'
  const page = seoPages.find((p) => p.path === path)
  if (!page) return { ...notFoundSeo, canonical: null }
  return {
    title: page.title,
    description: page.description,
    canonical: page.path === '/' ? `${SITE}/` : `${SITE}${page.path}`,
  }
}
