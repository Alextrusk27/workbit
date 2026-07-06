import { Container } from '@/components/ui/Container'
import { faq } from '@/content/faq'
import { usePageTitle } from '@/lib/usePageTitle'

export function FaqPage() {
  usePageTitle('Частые вопросы')
  return (
    <Container className="py-16 sm:py-24">
      <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
        FAQ
      </p>
      <h1 className="text-ink mt-4 max-w-2xl text-4xl sm:text-5xl">
        Частые вопросы
      </h1>

      <div className="divide-rule border-rule mt-12 max-w-2xl divide-y border-t border-b">
        {faq.map((item) => (
          <details key={item.q} className="group py-5">
            <summary className="text-ink font-display flex cursor-pointer list-none items-center justify-between gap-4 text-lg [&::-webkit-details-marker]:hidden">
              {item.q}
              <span
                aria-hidden
                className="text-accent shrink-0 transition-transform duration-200 group-open:rotate-45"
              >
                +
              </span>
            </summary>
            <p className="text-muted text-body-sm mt-3 leading-relaxed">
              {item.a}
            </p>
          </details>
        ))}
      </div>
    </Container>
  )
}
