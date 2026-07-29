import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { PlanCard } from '@/components/ui/PlanCard'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import { plans } from '@/content/plans'
import { useAuth } from '@/features/auth/useAuth'
import { usePageTitle } from '@/lib/usePageTitle'

export function PricingPage() {
  usePageTitle('Тарифы')
  const { isAuthenticated } = useAuth()
  const startTo = isAuthenticated ? '/app' : '/register'

  return (
    <>
      <PageHero
        title={
          <>
            Один тренажёр, <span className="text-grad">два режима</span>
          </>
        }
      >
        Начните бесплатно, чтобы понять формат. Переходите на Про, когда
        готовитесь всерьёз и нужен глубокий разбор.
      </PageHero>

      <section className="py-22">
        <Container>
          <div className="grid justify-center gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {plans.map((p, i) => (
              <Reveal key={p.name} delay={i * 0.05}>
                <PlanCard plan={p} features={p.features} to={startTo} />
              </Reveal>
            ))}
          </div>

          <p className="text-dim mt-7 text-center text-[13.5px]">
            Цены указаны для примера и могут измениться до запуска. Условия
            оплаты платного тарифа определяет{' '}
            <Link
              to="/offer"
              className="text-indigo hover:text-violet underline underline-offset-2 transition-colors"
            >
              Публичная оферта
            </Link>
            .
          </p>
        </Container>
      </section>

      <section className="pb-22">
        <Container>
          <Reveal>
            <CtaPanel
              title="Остались вопросы?"
              actions={
                <Link
                  to="/faq"
                  className={buttonClasses({ variant: 'secondary' })}
                >
                  Открыть FAQ
                </Link>
              }
            >
              Загляните в FAQ — там коротко о формате, профессиях и оценке
              ответов.
            </CtaPanel>
          </Reveal>
        </Container>
      </section>
    </>
  )
}
