import { Link } from 'react-router-dom'
import { Container } from '@/components/ui/Container'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { usePageTitle } from '@/lib/usePageTitle'

export function InterviewStubPage() {
  usePageTitle('Интервью')

  return (
    <Container className="py-12 sm:py-16">
      <Link
        to="/app"
        className="text-accent hover:text-accent-hover mb-6 inline-block text-sm transition-colors"
      >
        ← Личный кабинет
      </Link>
      <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
        Интервью
      </p>
      <h1 className="text-ink mt-4 text-3xl sm:text-4xl">
        Интервью под вакансию
      </h1>
      <p className="text-muted mt-4 max-w-xl leading-relaxed">
        Режим собеседования под конкретную вакансию с hh.ru сейчас на
        переработке. Мы возвращаем его отдельным, более точным сценарием — скоро
        он появится здесь.
      </p>
      <p className="text-muted mt-4 max-w-xl leading-relaxed">
        А пока потренируйтесь на вопросах под профессию и уровень в тренажёре.
      </p>

      <div className="mt-8 flex flex-wrap gap-3">
        <Link to="/app/training" className={buttonClasses()}>
          Перейти в тренажёр
        </Link>
        <Link to="/app" className={buttonClasses({ variant: 'secondary' })}>
          В личный кабинет
        </Link>
      </div>
    </Container>
  )
}
