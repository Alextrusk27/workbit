import { useId, useState, type CSSProperties, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { accentCardClasses } from '@/components/ui/cardStyles'
import { LimitIcon, Limits } from '@/components/ui/LimitIcon'
import { TOPUP, normalizeLimits, topUp, topUpQuote } from '@/content/limits'
import { cn } from '@/lib/cn'

const DEFAULT_LIMITS = 200
const DISCOUNT_FROM = TOPUP.tiers[1].from
const MAX_PERCENT = Math.round(
  (1 - TOPUP.tiers.at(-1)!.price / TOPUP.tiers[0].price) * 100,
)

function rub(value: number): string {
  return value.toLocaleString('ru-RU')
}

const STOPS = TOPUP.tiers.map((tier) => tier.from)
const SEGMENT = 100
const SLIDER_MAX = (STOPS.length - 1) * SEGMENT
const THUMB = 20
const THUMB_UNITS = 15

function unitToLimits(unit: number): number {
  const i = Math.min(Math.floor(unit / SEGMENT), STOPS.length - 2)
  const t = (unit - i * SEGMENT) / SEGMENT
  return normalizeLimits(STOPS[i] + t * (STOPS[i + 1] - STOPS[i]))
}

function limitsToUnit(limits: number): number {
  const last = STOPS.length - 1
  if (limits >= STOPS[last]) return SLIDER_MAX
  const i = STOPS.findIndex((_, k) => k === last || limits < STOPS[k + 1])
  return (
    i * SEGMENT + ((limits - STOPS[i]) / (STOPS[i + 1] - STOPS[i])) * SEGMENT
  )
}

function stopLeft(i: number): string {
  const f = i / (STOPS.length - 1)
  return `calc(${f * 100}% + ${THUMB / 2 - THUMB * f}px)`
}

interface TopUpCardProps {
  onBuy?: (limits: number) => void
  onLogin?: (limits: number) => void
  to?: string
  initialLimits?: number
  ctaLabel?: string
  disabled?: boolean
  className?: string
  children?: ReactNode
  heading?: 'h2' | 'h3'
}

export function TopUpCard({
  onBuy,
  onLogin,
  to = '/login',
  initialLimits = DEFAULT_LIMITS,
  ctaLabel = topUp.cta,
  disabled,
  className,
  children,
  heading: Heading = 'h3',
}: TopUpCardProps) {
  const inputId = useId()
  const [raw, setRaw] = useState(String(initialLimits))
  const [limits, setLimits] = useState(initialLimits)
  const quote = topUpQuote(limits)
  const oldAmount = quote.amount + quote.saving
  const percent = Math.round((quote.saving / oldAmount) * 100)
  const unit = limitsToUnit(limits)

  const pick = (value: number) => {
    setLimits(value)
    setRaw(String(value))
  }
  const commit = () => pick(normalizeLimits(Number(raw)))

  return (
    <div
      className={cn(
        accentCardClasses,
        'relative flex h-full flex-col',
        className,
      )}
    >
      <Heading className="text-ink pr-9 text-[22px] font-bold">
        {topUp.name}
      </Heading>

      <div className="mt-5 grid grid-cols-[auto_minmax(0,1fr)] items-end gap-x-4 sm:flex sm:justify-between sm:gap-x-6">
        <div>
          <label
            htmlFor={inputId}
            className="text-muted block text-[13.5px] font-semibold"
          >
            Лимиты
          </label>
          <div className="relative mt-[7px] w-[6.5rem] sm:w-[7.5rem]">
            <input
              id={inputId}
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
              className={cn(
                'border-line bg-surface text-ink no-spin h-12 w-full rounded-md border pr-9 pl-3.5 text-[17px] font-semibold tabular-nums',
                'focus:border-indigo focus:ring-indigo/18 transition-colors focus:ring-[3px] focus:outline-none',
              )}
            />
            <LimitIcon className="text-muted pointer-events-none absolute top-1/2 right-3.5 size-[15px] -translate-y-1/2" />
          </div>
        </div>

        <div className="contents sm:block sm:min-w-0 sm:text-right">
          <p className="text-ink min-w-0 text-right text-[28px] leading-none font-extrabold tracking-[-0.03em] tabular-nums sm:text-[36px]">
            {rub(quote.amount)} ₽
          </p>
          <p
            className={cn(
              'text-muted col-span-2 mt-2 flex items-center justify-end gap-2 text-[13.5px] whitespace-nowrap tabular-nums',
              quote.saving === 0 && 'invisible',
            )}
          >
            <s className="text-dim">{rub(oldAmount)} ₽</s>
            <span className="text-violet-strong font-semibold">
              −{rub(quote.saving)} ₽
            </span>
            <span className="bg-violet/13 border-violet/22 text-violet-strong rounded-full border px-2.5 py-[2px] text-xs font-bold whitespace-nowrap">
              −{percent}%
            </span>
          </p>
        </div>
      </div>

      <div className="mt-6">
        <p className="text-muted mb-3 text-[13px]">
          <span className="text-violet-strong font-semibold">
            Скидка до {MAX_PERCENT}%
          </span>{' '}
          при покупке от <Limits value={DISCOUNT_FROM} />
        </p>
        <div className="relative">
          <input
            type="range"
            aria-label="Лимиты"
            min={0}
            max={SLIDER_MAX}
            step={1}
            value={unit}
            onChange={(e) => pick(unitToLimits(Number(e.target.value)))}
            className="range-limits block w-full"
            style={
              {
                '--range-pct': `${(unit / SLIDER_MAX) * 100}%`,
              } as CSSProperties
            }
          />
          {STOPS.map((stop, i) => (
            <span
              key={stop}
              aria-hidden
              className={cn(
                'bg-pop pointer-events-none absolute top-1/2 size-1 -translate-x-1/2 -translate-y-1/2 rounded-full',
                Math.abs(i * SEGMENT - unit) < THUMB_UNITS && 'opacity-0',
              )}
              style={{ left: stopLeft(i) }}
            />
          ))}
        </div>
        <div className="relative mt-1 h-9">
          {TOPUP.tiers.map((tier, i) => {
            const next = TOPUP.tiers[i + 1]
            const active = limits >= tier.from && (!next || limits < next.from)
            return (
              <button
                key={tier.from}
                type="button"
                onClick={() => pick(tier.from)}
                aria-pressed={active}
                className={cn(
                  'hover:bg-indigo/8 absolute top-0 flex -translate-x-1/2 flex-col items-center rounded-lg px-2 py-1.5 transition-colors',
                  active && 'bg-indigo/12 hover:bg-indigo/12',
                )}
                style={{ left: stopLeft(i) }}
              >
                <Limits
                  value={tier.from}
                  className={cn(
                    'text-[13px] font-semibold',
                    active ? 'text-ink' : 'text-muted',
                  )}
                />
              </button>
            )
          })}
        </div>
      </div>

      {children}

      {onBuy ? (
        <button
          type="button"
          onClick={() => onBuy(limits)}
          disabled={disabled}
          className={buttonClasses({ className: 'mt-6 w-full' })}
        >
          {ctaLabel}
        </button>
      ) : onLogin ? (
        <button
          type="button"
          onClick={() => onLogin(limits)}
          className={buttonClasses({ className: 'mt-6 w-full' })}
        >
          {ctaLabel}
        </button>
      ) : (
        <Link to={to} className={buttonClasses({ className: 'mt-6 w-full' })}>
          {ctaLabel}
        </Link>
      )}

      <p className="text-dim mt-3 text-[13px] leading-relaxed">
        Условия оплаты определяет{' '}
        <Link
          to="/offer"
          target="_blank"
          rel="noopener noreferrer"
          className="text-muted hover:text-ink whitespace-nowrap underline underline-offset-2"
        >
          Публичная оферта
        </Link>
        .
      </p>
    </div>
  )
}
