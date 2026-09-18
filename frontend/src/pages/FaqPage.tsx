import { useState } from 'react'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { FaqList } from '@/components/marketing/FaqList'
import { HeroTitle } from '@/components/marketing/HeroTitle'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import { faq as faqItems } from '@/content/faq'
import { faq } from '@/content/pages/faq'
import { inline } from '@/lib/inline'

const { hero, cta } = faq

export function FaqPage() {
  const [copied, setCopied] = useState(false)

  const copySupportEmail = async () => {
    try {
      await navigator.clipboard.writeText(cta.email)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      window.location.href = `mailto:${cta.email}`
    }
  }

  return (
    <>
      <PageHero title={<HeroTitle hero={hero} />}>{inline(hero.text)}</PageHero>

      <section className="py-10 sm:py-16">
        <Container>
          <FaqList items={faqItems} />
        </Container>
      </section>

      <section className="pb-16">
        <Container>
          <Reveal>
            <CtaPanel
              title={cta.title}
              actions={
                <button
                  type="button"
                  onClick={copySupportEmail}
                  className={buttonClasses({ variant: 'secondary' })}
                >
                  {copied ? cta.copied : cta.copy}
                </button>
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
