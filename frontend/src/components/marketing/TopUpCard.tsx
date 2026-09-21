import { useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Chip } from '@/components/ui/Chip'
import { Field } from '@/components/ui/Field'
import { TOPUP, normalizeLimits, topUp, topUpQuote } from '@/content/limits'
import { cn } from '@/lib/cn'
import { limitsWord } from '@/lib/plural'

const DEFAULT_LIMITS = 200

function rub(value: number): string {
  return value.toLocaleString('ru-RU')
}

interface TopUpCardProps {
  /** Запуск оплаты на выбранное число лимитов; без него CTA — ссылка `to`. */
  onBuy?: (limits: number) => void
  to?: string
  ctaLabel?: string
  disabled?: boolean
  className?: string
  /** Уровень заголовка карточки в иерархии страницы. */
  heading?: 'h2' | 'h3'
}

/** Карточка пополнения: выбор числа лимитов, цена по ступеням и переход к оплате. */
export function TopUpCard({
  onBuy,
  to = '/pricing',
  ctaLabel = topUp.cta,
  disabled,
  className,
  heading: Heading = 'h3',
}: TopUpCardProps) {
  const [raw, setRaw] = useState(String(DEFAULT_LIMITS))
  const [limits, setLimits] = useState(DEFAULT_LIMITS)
  const quote = topUpQuote(limits)

  const pick = (value: number) => {
    setLimits(value)
    setRaw(String(value))
  }
  const commit = () => pick(normalizeLimits(Number(raw)))

  return (
    <div
      className={cn(
        'border-violet/50 bg-grad-plan shadow-plan relative flex h-full flex-col rounded-2xl border p-8 sm:px-[30px]',
        className,
      )}
    >
      <Heading className="text-ink text-[22px] font-bold">{topUp.name}</Heading>
      <p className="text-muted mt-2 text-[14.5px]">{topUp.lead}</p>

      <p className="text-ink mt-4 text-[36px] leading-none font-extrabold tracking-[-0.03em] tabular-nums">
        {rub(quote.amount)} ₽
      </p>
      <p className="text-muted mt-2.5 flex items-center gap-2.5 text-[14.5px] tabular-nums">
        {rub(quote.perLimit)} ₽ за лимит
        {quote.discount > 0 && (
          <span className="bg-violet/13 border-violet/22 text-violet-strong rounded-full border px-2.5 py-[2px] text-xs font-bold whitespace-nowrap">
            −{quote.discount}%
          </span>
        )}
      </p>

      <Field
        label="Сколько лимитов"
        type="number"
        inputMode="numeric"
        min={TOPUP.min}
        max={TOPUP.max}
        step={TOPUP.step}
        value={raw}
        onChange={(e) => {
          setRaw(e.target.value)
          const next = Number(e.target.value)
          if (next >= TOPUP.min) setLimits(normalizeLimits(next))
        }}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') commit()
        }}
        hint={`От ${TOPUP.min} до ${rub(TOPUP.max)}, кратно ${TOPUP.step}`}
        className="mt-4"
      />
      <input
        type="range"
        aria-label="Сколько лимитов"
        min={TOPUP.min}
        max={TOPUP.sliderMax}
        step={TOPUP.step}
        value={Math.min(limits, TOPUP.sliderMax)}
        onChange={(e) => pick(Number(e.target.value))}
        className="accent-indigo mt-3 w-full"
      />
      <div className="mt-3 flex flex-wrap gap-2">
        {TOPUP.presets.map((preset) => (
          <Chip
            key={preset}
            selected={limits === preset}
            onClick={() => pick(preset)}
          >
            {preset}
          </Chip>
        ))}
      </div>

      <ul className="border-line divide-divider mt-4 grid grid-cols-5 divide-x rounded-lg border text-center">
        {TOPUP.tiers.map((tier, i) => {
          const next = TOPUP.tiers[i + 1]
          const active = limits >= tier.from && (!next || limits < next.from)
          return (
            <li
              key={tier.from}
              className={cn('py-2.5', active && 'bg-indigo/12 text-ink')}
            >
              <p className="text-dim text-[11px]">от {tier.from}</p>
              <p
                className={cn(
                  'text-[13.5px] font-semibold tabular-nums',
                  active ? 'text-ink' : 'text-muted',
                )}
              >
                {rub(tier.price)} ₽
              </p>
            </li>
          )
        })}
      </ul>

      {onBuy ? (
        <button
          type="button"
          onClick={() => onBuy(limits)}
          disabled={disabled}
          className={buttonClasses({ className: 'mt-6 w-full' })}
        >
          {ctaLabel} {limits} {limitsWord(limits)} — {rub(quote.amount)} ₽
        </button>
      ) : (
        <Link to={to} className={buttonClasses({ className: 'mt-6 w-full' })}>
          {ctaLabel}
        </Link>
      )}
    </div>
  )
}
