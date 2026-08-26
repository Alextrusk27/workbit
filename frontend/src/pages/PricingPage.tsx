import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { PlanCard } from '@/components/ui/PlanCard'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import { SectionHead } from '@/components/marketing/SectionHead'
import { plans, promoActive } from '@/content/plans'
import { useAuth } from '@/features/auth/useAuth'
import { PAYMENT_ID_KEY, useCreatePayment } from '@/features/billing/useBilling'
import type { PaymentProduct } from '@/features/billing/api'
import { getErrorMessage } from '@/lib/api'

export function PricingPage() {
  const { isAuthenticated } = useAuth()
  const createPayment = useCreatePayment()
  const [error, setError] = useState<string | null>(null)
  const startTo = isAuthenticated ? '/app' : '/login'

  const buy = (product: PaymentProduct) => {
    if (createPayment.isPending) return
    setError(null)
    createPayment.mutate(product, {
      onSuccess: ({ paymentId, paymentUrl }) => {
        sessionStorage.setItem(PAYMENT_ID_KEY, paymentId)
        window.location.assign(paymentUrl)
      },
      onError: (e) => setError(getErrorMessage(e)),
    })
  }

  return (
    <>
      <PageHero
        title={
          <>
            Сколько стоит{' '}
            <span className="text-grad">подготовка к собеседованию</span>
          </>
        }
      >
        Начните бесплатно, чтобы понять формат. Переходите на Про, когда
        готовитесь всерьёз, — больше интервью, глубокие тренировки и динамика по
        вакансии.
      </PageHero>

      <section className="py-10 sm:py-16">
        <Container>
          {error && (
            <div className="mx-auto mb-6 max-w-[560px]">
              <Alert>{error}</Alert>
            </div>
          )}
          <div className="grid justify-center gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {plans.map((p, i) => (
              <Reveal key={p.name} delay={i * 0.05}>
                <PlanCard
                  plan={p}
                  features={p.features}
                  to={startTo}
                  heading="h2"
                  onSelect={
                    isAuthenticated && p.product
                      ? () => buy(p.product!)
                      : undefined
                  }
                  disabled={createPayment.isPending}
                />
              </Reveal>
            ))}
          </div>

          <p className="text-dim mt-7 text-center text-[13.5px]">
            {promoActive &&
              'До 1 октября — интервью в подарок к каждой покупке: +2 на Про и +5 на Максе. '}
            Тариф действует 30 дней с момента оплаты. Не хватило лимита —
            оплатите тариф ещё раз: срок продлится, а лимиты добавятся к
            оставшимся. Условия оплаты определяет{' '}
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

      <section className="pb-10 sm:pb-16">
        <Container>
          <Reveal>
            <SectionHead title="Тренажёр собеседований бесплатно">
              Старт доступен сразу после регистрации: банковская карта не
              нужна, автосписаний нет, а разовые бесплатные лимиты не сгорают
              со временем. Когда их перестанет хватать, Про даст 10 интервью и
              20 тренировок в месяц.
            </SectionHead>
          </Reveal>
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
