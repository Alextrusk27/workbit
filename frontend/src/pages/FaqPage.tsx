import { useState } from 'react'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { FaqList } from '@/components/marketing/FaqList'
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
            Частые вопросы{' '}
            <span className="text-grad">о тренажёре собеседований</span>
          </>
        }
      >
        Коротко о формате, профессиях, оценке ответов и тарифах.
      </PageHero>

      <section className="py-10 sm:py-16">
        <Container>
          <FaqList items={faq} />
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
              Не нашёл ответ? Напиши нам на{' '}
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
