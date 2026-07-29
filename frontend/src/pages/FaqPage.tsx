import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import { faq } from '@/content/faq'
import { useAuth } from '@/features/auth/useAuth'
import { usePageTitle } from '@/lib/usePageTitle'

export function FaqPage() {
  usePageTitle('Частые вопросы')
  const { isAuthenticated } = useAuth()
  const startTo = isAuthenticated ? '/app' : '/register'

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

      <section className="py-22">
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

      <section className="pb-22">
        <Container>
          <Reveal>
            <CtaPanel
              title="Готовы попробовать?"
              actions={
                <>
                  <Link to={startTo} className={buttonClasses()}>
                    Начать интервью
                  </Link>
                  <Link
                    to="/pricing"
                    className={buttonClasses({ variant: 'secondary' })}
                  >
                    Смотреть тарифы
                  </Link>
                </>
              }
            >
              Проведите пробное интервью сегодня и приходите на настоящее
              подготовленным.
            </CtaPanel>
          </Reveal>
        </Container>
      </section>
    </>
  )
}
