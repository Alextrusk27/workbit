import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { Limits } from '@/components/ui/LimitIcon'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { HeroTitle } from '@/components/marketing/HeroTitle'
import { PageHero } from '@/components/marketing/PageHero'
import { TopUpCard } from '@/components/marketing/TopUpCard'
import { Reveal } from '@/components/marketing/Reveal'
import { pricing } from '@/content/pages/pricing'
import { operations } from '@/content/limits'
import { useAuth } from '@/features/auth/useAuth'
import { PAYMENT_ID_KEY, useCreatePayment } from '@/features/billing/useBilling'
import { getErrorMessage } from '@/lib/api'
import { ctaLink } from '@/lib/cta'
import { inline } from '@/lib/inline'

const { hero, operations: operationsCopy, note, cta } = pricing

export function PricingPage() {
  const { isAuthenticated } = useAuth()
  const createPayment = useCreatePayment()
  const [error, setError] = useState<string | null>(null)
  const startTo = isAuthenticated ? '/app' : '/login'
  const faqLink = ctaLink(cta.primary, { start: '/app', isAuthenticated })

  const buy = (limits: number) => {
    if (createPayment.isPending) return
    setError(null)
    createPayment.mutate(limits, {
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
          <div className="grid gap-6 lg:grid-cols-2">
            <Reveal>
              <TopUpCard
                heading="h2"
                onBuy={isAuthenticated ? buy : undefined}
                to={startTo}
                disabled={createPayment.isPending}
              />
            </Reveal>
            <Reveal delay={0.05}>
              <div className="border-line bg-card flex h-full flex-col rounded-2xl border p-8 sm:px-[30px]">
                <h2 className="text-ink text-[22px] font-bold">
                  {operationsCopy.title}
                </h2>
                <p className="text-muted mt-2 text-[14.5px]">
                  {operationsCopy.lead}
                </p>
                <ul className="divide-divider mt-5 divide-y">
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
                        <Limits value={op.cost} />
                      </p>
                    </li>
                  ))}
                </ul>
              </div>
            </Reveal>
          </div>

          <p className="text-dim mt-7 text-center text-[13.5px]">
            {inline(note)}
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
