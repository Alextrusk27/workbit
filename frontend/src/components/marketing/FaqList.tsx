import { Reveal } from '@/components/marketing/Reveal'
import type { FaqItem } from '@/content/faq'

/** Аккордеон вопросов-ответов: список карточек `details`. */
export function FaqList({ items }: { items: FaqItem[] }) {
  return (
    <div className="mx-auto flex max-w-190 flex-col gap-3.5">
      {items.map((item, i) => (
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
  )
}
