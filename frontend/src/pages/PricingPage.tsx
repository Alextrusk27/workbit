import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { PlanCard } from '@/components/ui/PlanCard'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import { packs, plans } from '@/content/plans'
import { useAuth } from '@/features/auth/useAuth'
import { usePageTitle } from '@/lib/usePageTitle'

export function PricingPage() {
  usePageTitle('Тарифы')
  const { isAuthenticated } = useAuth()
  const startTo = isAuthenticated ? '/app' : '/login'

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
        готовитесь всерьёз — под реальные вакансии и голосом.
      </PageHero>

      <section className="py-16">
        <Container>
          <div className="grid justify-center gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {plans.map((p, i) => (
              <Reveal key={p.name} delay={i * 0.05}>
                <PlanCard plan={p} features={p.features} to={startTo} />
              </Reveal>
            ))}
          </div>

          <Reveal>
            <div className="border-line bg-card mt-6 flex flex-col items-center justify-between gap-5 rounded-2xl border p-7 sm:flex-row sm:px-8">
              <div className="text-center sm:text-left">
                <p className="text-ink text-[15px] font-semibold">
                  Не хватило пакета на месяц?
                </p>
                <p className="text-muted mt-1 text-sm">
                  Докупайте поверх любого тарифа — купленное не сгорает.
                </p>
              </div>
              <div className="flex flex-wrap justify-center gap-3">
                {packs.map((p) => (
                  <div
                    key={p.title}
                    className="border-line flex items-baseline gap-2.5 rounded-xl border px-4.5 py-3"
                  >
                    <span className="text-ink text-[14.5px] font-semibold">
                      {p.title}
                    </span>
                    <span className="text-muted text-sm tabular-nums">
                      {p.price}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </Reveal>

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

      <section className="pb-16">
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
