import { useState } from 'react'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import { faq } from '@/content/faq'

const SUPPORT_EMAIL = 'support@workbit.ru'

export function FaqPage() {
  const [copied, setCopied] = useState(false)

  const copySupportEmail = async () => {
    try {
      await navigator.clipboard.writeText(SUPPORT_EMAIL)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      window.location.href = `mailto:${SUPPORT_EMAIL}`
    }
  }

  return (
    <>
      <PageHero
        title={
          <>
            Частые <span className="text-grad">вопросы</span>
          </>
        }
      >
        Коротко о формате, профессиях, оценке ответов и тарифах.
      </PageHero>

      <section className="py-16">
        <Container>
          <div className="mx-auto flex max-w-190 flex-col gap-3.5">
            {faq.map((item, i) => (
              <Reveal key={item.q} delay={i * 0.03}>
                <details className="group border-line bg-card open:border-line-hover rounded-xl border transition-colors">
                  <summary className="text-ink flex cursor-pointer list-none items-center justify-between gap-4 px-5.5 py-4.5 font-semibold [&::-webkit-details-marker]:hidden">
                    {item.q}
                    <span
                      aria-hidden
                      className="text-indigo shrink-0 text-[22px] leading-none font-normal transition-transform duration-200 group-open:rotate-45"
                    >
                      +
                    </span>
                  </summary>
                  <p className="text-muted max-w-[68ch] px-5.5 pb-5 text-[14.5px]">
                    {item.a}
                  </p>
                </details>
              </Reveal>
            ))}
          </div>
        </Container>
      </section>

      <section className="pb-16">
        <Container>
          <Reveal>
            <CtaPanel
              title="Остались вопросы?"
              actions={
                <button
                  type="button"
                  onClick={copySupportEmail}
                  className={buttonClasses({ variant: 'secondary' })}
                >
                  {copied ? 'Адрес скопирован' : 'Скопировать адрес'}
                </button>
              }
            >
              Не нашли ответ? Напишите нам на{' '}
              <a
                href={`mailto:${SUPPORT_EMAIL}`}
                className="text-indigo hover:text-violet underline underline-offset-2 transition-colors"
              >
                {SUPPORT_EMAIL}
              </a>{' '}
              — поможем разобраться.
            </CtaPanel>
          </Reveal>
        </Container>
      </section>
    </>
  )
}
