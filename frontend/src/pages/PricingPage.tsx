import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { PlanCard } from '@/components/ui/PlanCard'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { HeroTitle } from '@/components/marketing/HeroTitle'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import { pricing } from '@/content/pages/pricing'
import { operations, packs, promo } from '@/content/packs'
import { useAuth } from '@/features/auth/useAuth'
import { PAYMENT_ID_KEY, useCreatePayment } from '@/features/billing/useBilling'
import type { PaymentProduct } from '@/features/billing/api'
import { getErrorMessage } from '@/lib/api'
import { ctaLink } from '@/lib/cta'
import { inline } from '@/lib/inline'
import { limitsWord } from '@/lib/plural'

const { hero, operations: operationsCopy, note, cta } = pricing

export function PricingPage() {
  const { isAuthenticated } = useAuth()
  const createPayment = useCreatePayment()
  const [error, setError] = useState<string | null>(null)
  const startTo = isAuthenticated ? '/app' : '/login'
  const faqLink = ctaLink(cta.primary, { start: '/app', isAuthenticated })

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
      <PageHero title={<HeroTitle hero={hero} />} />

      <section className="py-10 sm:py-16">
        <Container>
          {error && (
            <div className="mx-auto mb-6 max-w-[560px]">
              <Alert>{error}</Alert>
            </div>
          )}
          <div className="grid justify-center gap-6 sm:grid-cols-2 xl:grid-cols-4">
            {packs.map((p, i) => (
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
            {inline(promo.active ? `${promo.pricing} ${note}` : note)}
          </p>
        </Container>
      </section>

      <section className="pb-10 sm:pb-16">
        <Container>
          <Reveal>
            <div className="mx-auto max-w-[720px]">
              <h2 className="text-ink text-center text-[26px] font-bold tracking-[-0.02em]">
                {operationsCopy.title}
              </h2>
              <p className="text-muted mt-3 text-center text-[15px]">
                {operationsCopy.lead}
              </p>
              <ul className="border-line bg-card divide-divider mt-7 divide-y rounded-2xl border px-6">
                {operations.map((op) => (
                  <li
                    key={op.name}
                    className="flex items-baseline justify-between gap-6 py-4"
                  >
                    <div>
                      <p className="text-ink text-[15px] font-semibold">
                        {op.name}
                      </p>
                      <p className="text-dim mt-1 text-[13px]">{op.note}</p>
                    </div>
                    <p className="text-ink shrink-0 text-[15px] font-bold whitespace-nowrap tabular-nums">
                      {op.cost} {limitsWord(op.cost)}
                    </p>
                  </li>
                ))}
              </ul>
            </div>
          </Reveal>
        </Container>
      </section>

      <section className="pb-16">
        <Container>
          <Reveal>
            <CtaPanel
              title={cta.title}
              actions={
                <Link
                  {...faqLink}
                  className={buttonClasses({ variant: 'secondary' })}
                >
                  {cta.primary.label}
                </Link>
              }
            >
              {inline(cta.body)}
            </CtaPanel>
          </Reveal>
        </Container>
      </section>
    </>
  )
}
