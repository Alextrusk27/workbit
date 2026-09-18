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
import { plans, promo } from '@/content/plans'
import { useAuth } from '@/features/auth/useAuth'
import { PAYMENT_ID_KEY, useCreatePayment } from '@/features/billing/useBilling'
import type { PaymentProduct } from '@/features/billing/api'
import { getErrorMessage } from '@/lib/api'
import { ctaLink } from '@/lib/cta'
import { inline } from '@/lib/inline'

const { hero, note, cta } = pricing

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
            {inline(promo.active ? `${promo.pricing} ${note}` : note)}
          </p>
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
