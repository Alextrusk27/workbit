import type { Cta, Hero, PageSeo } from '@/content/types'

export type SkillsTrainerIcon = 'role' | 'clock' | 'chart'

export interface SkillsTrainerContent {
  hero: Hero & { text: string }
  flow: {
    title: string
    lead: string
    steps: { title: string; body: string }[]
  }
  report: {
    title: string
    body: string
    advantages: string[]
    demo: {
      title: string
      status: string
      cases: { question: string; score: number; note: string }[]
      noteLabel: string
      summary: { title: string; body: string; value: string }
    }
  }
  reference: {
    title: string
    lead: string
    note: string
    demo: {
      who: string
      badge: string
      question: string
      label: string
      answer: string
      vote: string
    }
  }
  start: {
    title: string
    steps: string[]
    link: Cta
    vacancy: {
      title: string
      company: string
      best: { label: string; stars: number; value: string }
      offer: { label: string; value: string }
      passed: string
      link: string
      status: string
      experience: string
      skillsLabel: string
      skills: { name: string; score: number }[]
      weak: { name: string; score: number; cta: Cta }
    }
  }
  audience: {
    title: string
    lead: string
    items: { icon: SkillsTrainerIcon; title: string; body: string }[]
    note: string
  }
  cta: { title: string; body: string; primary: Cta; note: string }
}

export const seo: PageSeo = {
  title: 'Тренажёр с вопросами и ответами ИИ для собеседования | Workbit',
  description:
    'Вопросы на собеседовании с ответами ИИ: десять вопросов по навыку, будь то разработка, бухгалтерия или право. Подготовка к техническим и другим собеседованиям.',
}

export const skillsTrainer: SkillsTrainerContent = {
  hero: {
    lead: 'Тренажёр вопросов для собеседования',
    accent: 'по навыкам',
    text: '**Вопросы как на реальном собеседовании.** Короткие сессии по десять вопросов',
  },
  flow: {
    title: 'Как проходит тренировка',
    lead: 'Короткая сессия из десяти вопросов, которую можно продлить',
    steps: [
      {
        title: 'Собери тренировку в три клика',
        body: 'Достаточно выбрать профессию, навык и уровень сложности. Вопросы по навыку будут привязаны к профессии.',
      },
      {
        title: 'Отвечай на вопросы',
        body: 'Отвечай по порядку на 10 вопросов, текстом или голосом. Если вопросов не хватило, можно продлить.',
      },
      {
        title: 'Получи отчёт',
        body: 'Балл за каждый ответ и правки рецензента приходят одним отчётом в конце. Результат сохраняется в истории тренировок.',
      },
    ],
  },
  report: {
    title: 'Итоговый разбор',
    body: 'После тренировки приходит один отчёт. Правки рецензента показывают, что верно, а что упущено.',
    advantages: [
      '**Один навык за сессию.** Глубже, чем «обо всём понемногу»',
      '**Эталонный ответ ИИ** после твоего ответа',
      '**Слабые места видно сразу.** Понятно, что подтянуть до собеседования',
      '**История тренировок хранит оценки.** Видно, что пройдено и с каким результатом',
    ],
    demo: {
      title: 'Отчёт · Налоговый учёт',
      status: '10 из 10 ответов разобраны',
      cases: [
        {
          question: '4. НДС с полученного аванса',
          score: 4,
          note: 'Верно про счёт-фактуру на аванс. Уточни срок её выставления и когда НДС принимают к вычету.',
        },
        {
          question: '5. Расхождения в акте сверки',
          score: 3,
          note: 'Порядок сверки верный, но упущена проверка периодов отражения документов.',
        },
      ],
      noteLabel: 'Правка рецензента',
      summary: {
        title: 'Итог тренировки',
        body: 'средний балл за 10 ответов, сохранён в истории',
        value: '3,8 из 5',
      },
    },
  },
  reference: {
    title: 'Эталонный ответ ИИ',
    lead: 'Посмотри, как может выглядеть идеальный ответ',
    note: 'Доступен по запросу, когда ты ответил на вопрос: во время тренировки или в итоговом разборе. На оценки это не влияет.',
    demo: {
      who: 'Вопрос 4 из 10 · Учёт НДС',
      badge: 'Эталонный ответ',
      question:
        'НДС с полученного аванса — как начислить и что происходит при отгрузке?',
      label: 'Эталон · сгенерирован ИИ',
      answer:
        'При получении аванса продавец начисляет НДС по расчётной ставке 20/120 и в течение 5 календарных дней выставляет авансовый счёт-фактуру. При отгрузке НДС начисляется со всей стоимости отгрузки, а налог с аванса принимается к вычету — двойного налогообложения не возникает.',
      vote: 'Эталонный ответ полезен?',
    },
  },
  start: {
    title: 'С чего начать?',
    steps: [
      '**Пройди ИИ-интервью по вакансии** как репетицию настоящего собеседования.',
      '**Открой отчёт.** В нём оценка по каждому навыку, слабые видно сразу.',
      '**Забирай слабые навыки в тренажёр** по одному, пока оценка не вырастет.',
    ],
    link: { label: 'Пройти ИИ-интервью по вакансии →', to: '/ai-interview' },
    vacancy: {
      title: 'Интернет-маркетолог',
      company: 'Агентство «Медиаполе» · Казань',
      best: { label: 'Лучший результат:', stars: 3.4, value: '3,4' },
      offer: { label: 'Оффер:', value: 'Средняя' },
      passed: 'Пройдено: 1 раз',
      link: 'Вакансия на hh.ru ↗',
      status: 'Активна',
      experience: 'Опыт: 3–6 лет',
      skillsLabel: 'Оценки по навыкам из отчёта',
      skills: [
        { name: 'Контекстная реклама', score: 4 },
        { name: 'Email-маркетинг', score: 4 },
      ],
      weak: {
        name: 'Веб-аналитика',
        score: 2,
        cta: {
          label: 'Тренировать →',
          train: { skill: 'Веб-аналитика', level: 'MEDIUM' },
        },
      },
    },
  },
  audience: {
    title: 'Вопросы на собеседовании',
    lead: 'Тренажёр подберёт их для любой профессии, с подходящим уровнем сложности',
    items: [
      {
        icon: 'role',
        title: 'Любая профессия',
        body: 'Разработчики, аналитики, бухгалтеры, юристы, маркетологи, дизайнеры, менеджеры проектов…',
      },
      {
        icon: 'clock',
        title: 'Короткий формат',
        body: 'Десять вопросов за подход: удобно тренироваться каждый день. Если не хватило, можно добавить.',
      },
      {
        icon: 'chart',
        title: 'История тренировок',
        body: 'Список всех тренировок с оценками.',
      },
    ],
    note: 'Разбираем по профессиям, что спрашивают на самом деле: [вопросы на собеседовании менеджера по продажам](/blog/interview-questions/sales-manager)',
  },
  cta: {
    title: 'Начни с одного навыка',
    body: 'Десять вопросов, разбор в конце',
    primary: { label: 'Начать тренировку', start: true },
    note: 'Первые тренировки бесплатно · [Вопросы о формате](/faq)',
  },
}
