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
    title: 'Workbit — тренажёр собеседований с AI',
    description:
      'Тренажёр собеседований с AI: интервью по вакансии с hh.ru и тренировка отдельного навыка, разбор каждого ответа и честная оценка — как на настоящем интервью.',
    sources: ['frontend/src/pages/HomePage.tsx'],
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
    title: 'AI-интервью — Workbit',
    description:
      'Собеседование, которое можно переиграть: полная сессия из вопросов под конкретную вакансию с hh.ru. Отвечаете текстом или голосом, в конце — вердикт и вероятность оффера.',
    sources: ['frontend/src/pages/AiInterviewPage.tsx'],
  },
  {
    path: '/skills-trainer',
    title: 'Тренажёр навыков — Workbit',
    description:
      'Короткие сессии по одному навыку: Spring Boot, многопоточность, SQL — что угодно. Вопросы подберёт AI-рецензент, разбор с оценками придёт в конце тренировки.',
    sources: ['frontend/src/pages/SkillsTrainerPage.tsx'],
  },
  {
    path: '/faq',
    title: 'Частые вопросы — Workbit',
    description:
      'Частые вопросы о Workbit: коротко о формате AI-интервью, профессиях, оценке ответов и тарифах.',
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
    title: 'Тарифы — Workbit',
    description: `Тарифы Workbit: ${plans
      .map((p) => `${p.name} — ${p.period ? `${p.price} в месяц` : p.price}`)
      .join(', ')}. Разовый платёж на 30 дней, без автосписаний.`,
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
    title: 'Политика конфиденциальности — Workbit',
    description:
      'Политика конфиденциальности и обработки персональных данных сервиса Workbit.',
    sources: ['docs/privacy-policy.md'],
  },
  {
    path: '/user-agreement',
    title: 'Пользовательское соглашение — Workbit',
    description:
      'Пользовательское соглашение сервиса Workbit: условия использования тренажёра собеседований.',
    sources: ['docs/user-agreement.md'],
  },
  {
    path: '/offer',
    title: 'Публичная оферта — Workbit',
    description: 'Публичная оферта сервиса Workbit: условия оплаты тарифов.',
    sources: ['docs/offer.md'],
  },
]

export const notFoundSeo = {
  title: 'Страница не найдена — Workbit',
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
