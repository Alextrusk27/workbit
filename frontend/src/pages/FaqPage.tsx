import { Container } from '@/components/ui/Container'
import { faq } from '@/content/faq'
import { usePageTitle } from '@/lib/usePageTitle'

export function FaqPage() {
  usePageTitle('Частые вопросы')
  return (
    <Container className="py-16 sm:py-24">
      <p className="text-muted animate-rise font-mono text-xs tracking-[0.2em] uppercase">
        FAQ
      </p>
      <h1
        className="text-ink animate-rise mt-4 text-4xl sm:text-5xl"
        style={{ animationDelay: '80ms' }}
      >
        Частые вопросы
      </h1>

      <div
        className="divide-rule border-rule animate-rise mt-12 divide-y border-t border-b"
        style={{ animationDelay: '160ms' }}
      >
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
