import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { StatusTag } from '@/components/app/StatusTag'
import { Alert } from '@/components/ui/Alert'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Container } from '@/components/ui/Container'
import { Eyebrow } from '@/components/ui/Eyebrow'
import { Skeleton } from '@/components/ui/Skeleton'
import { Stars } from '@/components/ui/Stars'
import { buttonClasses } from '@/components/ui/buttonStyles'
import type {
  InterviewAttempt,
  InterviewVacancyDetail,
  OfferProbability,
  RecommendedTraining,
} from '@/features/interview/api'
import {
  OFFER_TONE,
  STATUS_LABELS,
  trainingLevelCode,
  VACANCY_STATUS_LABELS,
} from '@/features/interview/labels'
import { interviewCreateErrorMessage } from '@/features/interview/errors'
import {
  useCreateInterview,
  useDeleteInterviewVacancy,
  useInterviewVacancy,
} from '@/features/interview/useInterview'
import { useVacancyStatus } from '@/features/vacancy/useVacancy'
import { getErrorMessage } from '@/lib/api'
import { cn } from '@/lib/cn'
import { formatDate, formatDay, formatDayShort } from '@/lib/dates'
import { usePageTitle } from '@/lib/usePageTitle'

const OFFER_INLINE_CLASS = {
  low: 'text-ink',
  mid: 'text-indigo',
  high: 'text-ok',
} as const

const Y_AXIS = [
  { top: '0%', label: '★★★★★', short: '5' },
  { top: '25%', label: '★★★★', short: '4' },
  { top: '50%', label: '★★★', short: '3' },
  { top: '75%', label: '★★', short: '2' },
  { top: '100%', label: '★', short: '★' },
]

function formatScore(score: number): string {
  return score.toFixed(1).replace('.', ',')
}

function completedAttempts(detail: InterviewVacancyDetail): InterviewAttempt[] {
  return detail.interviews.filter(
    (i) => i.status === 'COMPLETED' && i.avgScore != null,
  )
}

/** Динамика: суммарное изменение оценки по линейному тренду точек графика
 *  (наклон МНК-прямой × число интервалов), округлённое до десятых. */
function trendDelta(scores: number[]): number {
  const n = scores.length
  const meanX = (n - 1) / 2
  const meanY = scores.reduce((a, b) => a + b, 0) / n
  let num = 0
  let den = 0
  scores.forEach((y, x) => {
    num += (x - meanX) * (y - meanY)
    den += (x - meanX) ** 2
  })
  return Math.round((num / den) * (n - 1) * 10) / 10
}

function OfferValue({
  value,
  className,
}: {
  value: OfferProbability
  className?: string
}) {
  return (
    <span
      className={cn(
        'font-semibold',
        OFFER_INLINE_CLASS[OFFER_TONE[value]],
        className,
      )}
    >
      {value}
    </span>
  )
}

export function InterviewVacancyPage() {
  const { vacancyId = '' } = useParams()
  const { data, isLoading, isError, error } = useInterviewVacancy(vacancyId)
  usePageTitle(data?.vacancyName ?? 'Вакансия')

  if (isLoading) {
    return (
      <Container>
        <div role="status">
          <span className="sr-only">Загрузка вакансии…</span>
          <Skeleton className="h-4 w-32" />
          <Skeleton className="mt-6 h-9 w-72" />
          <Skeleton className="mt-4 h-4 w-56" />
          <Skeleton className="mt-8 h-64" />
          <Skeleton className="mt-4 h-40" />
        </div>
      </Container>
    )
  }

  if (isError || !data) {
    return (
      <Container>
        <Link
          to="/app/interview"
          className="text-indigo hover:text-violet mb-7 inline-block text-sm transition-colors"
        >
          ← Мои интервью
        </Link>
        <Alert>{getErrorMessage(error)}</Alert>
      </Container>
    )
  }

  return (
    <Container>
      <VacancyHeader detail={data} />
      <ProgressSection detail={data} />
      <RecommendationsSection detail={data} />
      <AttemptsSection detail={data} />
    </Container>
  )
}

function VacancyHeader({ detail }: { detail: InterviewVacancyDetail }) {
  const navigate = useNavigate()
  const del = useDeleteInterviewVacancy()
  const create = useCreateInterview()
  const [confirming, setConfirming] = useState(false)
  const { data: live } = useVacancyStatus(detail.vacancyUrl)
  const unavailable = live != null && live.status !== 'ACTIVE'
  const pending = detail.interviews.filter((i) => i.status !== 'COMPLETED')
  const unfinished = pending[pending.length - 1]

  const onRetry = () => {
    if (!detail.vacancyUrl || create.isPending) return
    create.mutate(
      { vacancyUrl: detail.vacancyUrl },
      {
        onSuccess: (session) => navigate(`/app/interview/${session.id}`),
      },
    )
  }

  const onDelete = () => {
    setConfirming(false)
    del.mutate(detail.vacancyId, {
      onSuccess: () => navigate('/app/interview', { replace: true }),
    })
  }

  return (
    <div>
      <Link
        to="/app/interview"
        className="text-indigo hover:text-violet mb-7 inline-block text-sm transition-colors"
      >
        ← Мои интервью
      </Link>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0">
          <Eyebrow>Интервью · Вакансия</Eyebrow>
          <h1 className="text-ink mt-2.5 text-[clamp(28px,3.6vw,38px)] font-extrabold break-words">
            {detail.vacancyName}
          </h1>
          <div className="text-dim mt-2.5 flex flex-wrap items-center gap-x-3.5 gap-y-1 text-[12.5px]">
            <span>{detail.employer || 'Работодатель не указан'}</span>
            {detail.vacancyUrl && (
              <a
                href={detail.vacancyUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-indigo hover:text-violet transition-colors"
              >
                Вакансия на hh.ru ↗
              </a>
            )}
            {live && (
              <span
                className={cn(
                  'font-semibold',
                  live.status === 'ACTIVE' ? 'text-ok' : 'text-danger',
                )}
              >
                {VACANCY_STATUS_LABELS[live.status]}
              </span>
            )}
            {detail.experience && <span>Опыт: {detail.experience}</span>}
          </div>
        </div>
        <div className="flex items-center gap-2.5">
          {unfinished ? (
            <Link
              to={`/app/interview/${unfinished.sessionId}`}
              className={buttonClasses()}
            >
              Продолжить интервью
            </Link>
          ) : unavailable ? (
            <span className="text-dim inline-flex h-11 cursor-not-allowed items-center justify-center rounded-lg bg-[rgba(148,163,184,0.12)] px-6 text-[15px] font-semibold select-none">
              Вакансия недоступна
            </span>
          ) : (
            <button
              type="button"
              onClick={onRetry}
              disabled={create.isPending}
              className={buttonClasses()}
            >
              {create.isPending ? 'Готовим вопросы…' : 'Пройти ещё раз'}
            </button>
          )}
          <button
            type="button"
            onClick={() => setConfirming(true)}
            disabled={del.isPending}
            className={buttonClasses({ variant: 'danger' })}
          >
            {del.isPending ? 'Удаляем…' : 'Удалить'}
          </button>
        </div>
      </div>

      {create.isError && (
        <div className="mt-5">
          <Alert>{interviewCreateErrorMessage(create.error)}</Alert>
        </div>
      )}

      {del.isError && (
        <div className="mt-5">
          <Alert>{getErrorMessage(del.error)}</Alert>
        </div>
      )}

      <ConfirmDialog
        open={confirming}
        title="Удалить вакансию?"
        text={`Все интервью по вакансии «${detail.vacancyName}» будут удалены вместе с ней.`}
        onConfirm={onDelete}
        onClose={() => setConfirming(false)}
      />
    </div>
  )
}

function HintTip({ text }: { text: string }) {
  return (
    <span
      tabIndex={0}
      className="group border-line text-dim relative inline-flex size-[15px] shrink-0 cursor-help items-center justify-center rounded-full border text-[10px] font-normal"
    >
      ?
      <span className="bg-pop border-line text-ink shadow-pop pointer-events-none invisible absolute bottom-[calc(100%+8px)] left-1/2 z-5 w-max max-w-60 -translate-x-1/2 rounded-lg border px-[11px] py-[7px] text-center text-xs leading-snug font-normal opacity-0 transition-opacity group-hover:visible group-hover:opacity-100 group-focus:visible group-focus:opacity-100">
        {text}
      </span>
    </span>
  )
}

function ProgressSection({ detail }: { detail: InterviewVacancyDetail }) {
  const points = completedAttempts(detail).slice(-5)
  if (points.length === 0) {
    return (
      <section className="border-line bg-card mt-7 rounded-xl border px-5 py-5 sm:px-6 sm:py-5.5">
        <h2 className="text-ink m-0 text-[15px] font-semibold">
          Прогресс по вакансии
        </h2>
        <p className="text-dim mt-2 text-[12.5px] italic">
          Заверши интервью и узнай оценку и шансы на оффер
        </p>
      </section>
    )
  }

  const completed = completedAttempts(detail)
  const best = completed.reduce((a, b) => (b.avgScore! > a.avgScore! ? b : a))
  const delta =
    points.length >= 2 ? trendDelta(points.map((p) => p.avgScore!)) : null

  const xs = points.map((_, i) => 10 + i * 20)
  const ys = points.map((p) => ((5 - p.avgScore!) / 4) * 100)
  const linePath = points
    .map((_, i) => `${i === 0 ? 'M' : 'L'}${xs[i]} ${ys[i]}`)
    .join(' ')
  const areaPath = `${linePath} L${xs[points.length - 1]} 100 L${xs[0]} 100 Z`
  const slots = Array.from({ length: 5 }, (_, i) => i)

  const tileClass =
    'border-line bg-glass min-w-0 rounded-[10px] border px-3 py-2.5 sm:rounded-none sm:border-0 sm:bg-transparent sm:p-0'

  return (
    <section className="border-line bg-card mt-7 rounded-xl border px-5 py-5 sm:px-6 sm:py-5.5">
      <div className="flex flex-wrap items-start justify-between gap-x-8 gap-y-4">
        <div>
          <h2 className="text-ink m-0 flex items-center gap-[7px] text-[15px] font-semibold">
            Прогресс по вакансии
            <HintTip text="На графике — 5 последних интервью, лучшая оценка и оффер — по всем" />
          </h2>
          <p className="text-muted mt-1 text-[13.5px]">Оценка за попытку</p>
        </div>
        <div className="grid w-full grid-cols-3 gap-3 sm:flex sm:w-auto sm:flex-wrap sm:gap-x-9 sm:gap-y-4">
          <div className={tileClass}>
            <p className="text-ink m-0 flex items-center gap-2 text-[17px] leading-[26px] font-bold tabular-nums sm:text-[19px] sm:leading-normal">
              {formatScore(best.avgScore!)}
              <Stars
                value={Math.round(best.avgScore! * 2) / 2}
                className="text-xs max-sm:hidden"
              />
              <span className="text-star text-xs sm:hidden">★</span>
            </p>
            <p className="text-dim mt-[3px] text-[11px] sm:text-xs">
              <span className="sm:hidden">Лучшая</span>
              <span className="max-sm:hidden">Лучшая оценка</span>
            </p>
          </div>
          <div className={tileClass}>
            <p
              className={cn(
                'm-0 text-[17px] leading-[26px] font-bold tabular-nums sm:text-[19px] sm:leading-normal',
                !delta && 'text-dim',
                delta != null && delta > 0 && 'text-ok',
                delta != null && delta < 0 && 'text-danger',
              )}
            >
              {delta == null
                ? '—'
                : `${delta > 0 ? '+' : ''}${formatScore(delta)}`}
            </p>
            <p className="text-dim mt-[3px] flex items-center gap-1.5 text-[11px] sm:text-xs">
              Динамика
              <HintTip text="Общее направление оценок на графике — от первой попытки к последней" />
            </p>
          </div>
          {best.offerProbability && (
            <div className={tileClass}>
              <p className="m-0 text-[15px] leading-[26px] font-bold sm:text-[19px] sm:leading-normal">
                <OfferValue value={best.offerProbability} />
              </p>
              <p className="text-dim mt-[3px] flex items-center gap-1.5 text-[11px] sm:text-xs">
                Оффер
                <HintTip text="Вероятность оффера в лучшем интервью" />
              </p>
            </div>
          )}
        </div>
      </div>

      <div className="mt-9 grid grid-cols-[26px_1fr] sm:mt-10 sm:grid-cols-[52px_1fr]">
        <div className="relative h-[150px] sm:h-[170px]">
          {Y_AXIS.map((y) => (
            <span
              key={y.top}
              style={{ top: y.top }}
              className="absolute right-2 -translate-y-1/2 text-[10px] whitespace-nowrap sm:right-2.5 sm:tracking-[1px] sm:opacity-85"
            >
              <span
                className={cn(
                  'tabular-nums sm:hidden',
                  y.top === '100%' ? 'text-star' : 'text-dim',
                )}
              >
                {y.short}
              </span>
              <span className="text-star max-sm:hidden">{y.label}</span>
            </span>
          ))}
        </div>
        <div className="relative h-[150px] sm:h-[170px]">
          {Y_AXIS.map((y) => (
            <div
              key={y.top}
              style={{ top: y.top }}
              className={cn(
                'absolute right-0 left-0 h-0 border-t',
                y.top === '100%'
                  ? 'border-glass-line sm:border-line'
                  : 'border-line sm:border-dashed',
              )}
            />
          ))}
          <svg
            viewBox="0 0 100 100"
            preserveAspectRatio="none"
            className="absolute inset-0 block h-full w-full"
            aria-hidden="true"
          >
            <defs>
              <linearGradient id="pgLine" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0" stopColor="#818cf8" />
                <stop offset="1" stopColor="#a78bfa" />
              </linearGradient>
              <linearGradient id="pgArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stopColor="#818cf8" stopOpacity="0.16" />
                <stop offset="1" stopColor="#818cf8" stopOpacity="0" />
              </linearGradient>
            </defs>
            {points.length >= 2 && (
              <>
                <path d={areaPath} fill="url(#pgArea)" />
                <path
                  d={linePath}
                  fill="none"
                  stroke="url(#pgLine)"
                  className="[stroke-width:2] sm:[stroke-width:2.5]"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  vectorEffect="non-scaling-stroke"
                />
              </>
            )}
          </svg>
          {points.map((p, i) => (
            <span
              key={p.sessionId}
              style={{ left: `${xs[i]}%`, top: `${ys[i]}%` }}
              className={cn(
                'absolute -translate-x-1/2 -translate-y-full text-[11px] font-semibold tabular-nums sm:mt-[-12px] sm:text-[12.5px]',
                i === points.length - 1
                  ? 'text-ink border-indigo/45 bg-indigo/[0.18] sm:text-indigo mt-[-10px] rounded-full border px-[7px] py-px font-bold sm:border-0 sm:bg-transparent sm:p-0 sm:font-semibold'
                  : 'text-muted sm:text-ink mt-[-9px]',
              )}
            >
              {formatScore(p.avgScore!)}
            </span>
          ))}
          {slots.map((i) =>
            i < points.length ? (
              <div
                key={i}
                style={{ left: `${10 + i * 20}%`, top: `${ys[i]}%` }}
                className={cn(
                  'absolute -translate-x-1/2 -translate-y-1/2 rounded-full sm:size-2.5 sm:shadow-[0_0_0_3px_rgba(129,140,248,0.18)]',
                  i === points.length - 1
                    ? 'size-[9px] bg-[#a78bfa] shadow-[0_0_0_3px_rgba(167,139,250,0.22)]'
                    : 'size-2 bg-[#818cf8]',
                )}
              />
            ) : (
              <div
                key={i}
                style={{ left: `${10 + i * 20}%`, top: '100%' }}
                className="bg-glass-line sm:border-glass-line absolute h-[7px] w-[2px] -translate-x-1/2 -translate-y-1/2 rounded-sm sm:size-2.5 sm:rounded-full sm:border-[1.5px] sm:border-dashed sm:bg-transparent"
              />
            ),
          )}
        </div>
      </div>
      <div className="mt-2 grid grid-cols-[26px_1fr] sm:mt-2.5 sm:grid-cols-[52px_1fr]">
        <span />
        <div className="flex">
          {slots.map((i) => (
            <span
              key={i}
              className={cn(
                'text-dim flex-1 text-center text-[11px] sm:text-xs',
                i >= points.length && 'opacity-50 sm:opacity-55',
              )}
            >
              {i < points.length ? (
                <>
                  <span className="sm:hidden">
                    {formatDayShort(points[i].completedAt ?? points[i].created)}
                  </span>
                  <span className="max-sm:hidden">
                    {formatDay(points[i].completedAt ?? points[i].created)}
                  </span>
                </>
              ) : (
                <>
                  <span className="sm:hidden">·</span>
                  <span className="max-sm:hidden">
                    {`Интервью ${completed.length + (i - points.length) + 1}`}
                  </span>
                </>
              )}
            </span>
          ))}
        </div>
      </div>
    </section>
  )
}

function RecommendationsSection({
  detail,
}: {
  detail: InterviewVacancyDetail
}) {
  if (detail.recommendedTrainings.length === 0) return null

  const trainingNewTo = (skill: string) => {
    const params = new URLSearchParams({
      skill,
      level: trainingLevelCode(detail.experience),
    })
    return `/app/training/new?${params}`
  }

  return (
    <section className="border-line bg-card mt-4 rounded-xl border px-6 py-5.5">
      <h2 className="text-ink m-0 text-[15px] font-semibold">
        Рекомендованные тренировки
      </h2>
      <p className="text-muted mt-1 text-[13.5px]">
        По отстающим навыкам из твоих интервью
      </p>
      <ul className="mt-3.5 flex flex-col gap-2.5">
        {detail.recommendedTrainings.map((r) => (
          <RecommendationRow
            key={r.skill}
            rec={r}
            trainingNewTo={trainingNewTo}
          />
        ))}
      </ul>
    </section>
  )
}

function RecommendationRow({
  rec,
  trainingNewTo,
}: {
  rec: RecommendedTraining
  trainingNewTo: (skill: string) => string
}) {
  const completed = rec.trainingStatus === 'COMPLETED'
  const inProgress =
    rec.trainingStatus === 'CREATED' || rec.trainingStatus === 'IN_PROGRESS'

  return (
    <li className="border-line flex flex-wrap items-center gap-x-3 gap-y-2 rounded-xl border px-3.5 py-3">
      <div className="min-w-0 flex-1 max-sm:basis-full">
        <p className="text-ink m-0 text-sm font-semibold break-words">
          {rec.skill}
        </p>
        <p className="text-dim mt-0.5 text-[12.5px]">
          Слабое место интервью
          {rec.interviewScore != null &&
            ` · ${rec.interviewScore.toFixed(1).replace('.', ',')} в разборе`}
        </p>
      </div>
      {completed && (
        <>
          <StatusTag label="Завершено" done />
          {rec.trainingScore != null && (
            <span className="text-dim flex items-center gap-1.5 text-[12.5px]">
              <Stars
                value={Math.round(rec.trainingScore * 2) / 2}
                className="text-xs"
              />
              <span className="tabular-nums">
                {rec.trainingScore.toFixed(1).replace('.', ',')}
              </span>
            </span>
          )}
          <Link
            to={trainingNewTo(rec.skill)}
            className={cn(
              buttonClasses({ variant: 'secondary', size: 'sm' }),
              'min-w-24',
            )}
          >
            Повторить
          </Link>
        </>
      )}
      {inProgress && rec.trainingSessionId && (
        <>
          <StatusTag label="В процессе" done={false} />
          {rec.answeredCount != null && rec.totalQuestions != null && (
            <span className="text-dim text-[12.5px] tabular-nums">
              {rec.answeredCount}/{rec.totalQuestions} вопросов
            </span>
          )}
          <Link
            to={`/app/training/${rec.trainingSessionId}`}
            className={cn(
              buttonClasses({ variant: 'secondary', size: 'sm' }),
              'min-w-24',
            )}
          >
            Продолжить
          </Link>
        </>
      )}
      {!completed && !inProgress && (
        <>
          <span className="text-dim rounded-sm bg-[rgba(148,163,184,0.14)] px-2.5 py-[3px] text-xs font-semibold">
            Не начато
          </span>
          <Link
            to={trainingNewTo(rec.skill)}
            className={cn(
              buttonClasses({ variant: 'secondary', size: 'sm' }),
              'min-w-24',
            )}
          >
            Тренировать
          </Link>
        </>
      )}
    </li>
  )
}

function AttemptsSection({ detail }: { detail: InterviewVacancyDetail }) {
  return (
    <section className="border-line bg-card mt-4 rounded-xl border px-6 py-5.5">
      <h2 className="text-ink m-0 text-[15px] font-semibold">Мои интервью</h2>
      <ol className="border-line mt-2.5 border-t">
        {detail.interviews.map((attempt, i) => (
          <AttemptRow key={attempt.sessionId} attempt={attempt} index={i} />
        ))}
      </ol>
    </section>
  )
}

function AttemptRow({
  attempt,
  index,
}: {
  attempt: InterviewAttempt
  index: number
}) {
  const completed = attempt.status === 'COMPLETED'

  return (
    <li className="border-line text-dim flex flex-wrap items-center gap-x-3.5 gap-y-1.5 border-t py-2.5 text-[12.5px] first:border-t-0 sm:grid sm:grid-cols-[90px_118px_80px_32px_1fr_auto]">
      <span className="text-ink font-medium">Интервью {index + 1}</span>
      <span>{formatDate(attempt.created)}</span>
      {completed && attempt.avgScore != null ? (
        <>
          <Stars
            value={Math.round(attempt.avgScore * 2) / 2}
            className="text-xs"
          />
          <span className="tabular-nums">{formatScore(attempt.avgScore)}</span>
          <span>
            {attempt.offerProbability && (
              <>
                Оффер: <OfferValue value={attempt.offerProbability} />
              </>
            )}
          </span>
          <span className="ml-auto flex items-center gap-3.5">
            <Link
              to={`/app/interview/${attempt.sessionId}/report`}
              className="text-indigo hover:text-violet text-[12.5px] transition-colors"
            >
              Разбор
            </Link>
          </span>
        </>
      ) : (
        <>
          <span className="col-span-3">
            <StatusTag label={STATUS_LABELS[attempt.status]} done={false} />
          </span>
          <span className="ml-auto flex items-center gap-3.5">
            <Link
              to={`/app/interview/${attempt.sessionId}`}
              className="text-indigo hover:text-violet text-[12.5px] transition-colors"
            >
              Продолжить
            </Link>
          </span>
        </>
      )}
    </li>
  )
}
