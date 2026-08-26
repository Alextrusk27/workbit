import type { FormEvent } from 'react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import { Eyebrow } from '@/components/ui/Eyebrow'
import { Field } from '@/components/ui/Field'
import { Skeleton } from '@/components/ui/Skeleton'
import { buttonClasses } from '@/components/ui/buttonStyles'
import type { VacancyPreview } from '@/features/vacancy/api'
import { takePendingVacancyUrl } from '@/features/vacancy/pendingVacancy'
import {
  hhVacancyId,
  isHhVacancyUrl,
  useVacancyPreview,
} from '@/features/vacancy/useVacancy'
import {
  useCreateInterview,
  useInterviewVacancy,
} from '@/features/interview/useInterview'
import { interviewCreateErrorMessage } from '@/features/interview/errors'
import { ApiRequestError } from '@/lib/api'
import { useDebounced } from '@/lib/useDebounced'
import { usePageTitle } from '@/lib/usePageTitle'

function PreviewCard({ preview }: { preview: VacancyPreview }) {
  const rows = [
    { label: 'Работодатель', value: preview.employer },
    { label: 'Зарплата', value: preview.salary },
    { label: 'Опыт', value: preview.experience },
  ].filter((r) => r.value)

  return (
    <div className="border-line bg-card rounded-xl border px-6 py-5.5">
      <Eyebrow>Вакансия</Eyebrow>
      <h2 className="text-ink mt-2 text-[19px] font-bold tracking-[-0.01em] break-words">
        {preview.name}
      </h2>
      <dl className="mt-3.5 flex flex-col gap-1.5 text-sm">
        {rows.map((r) => (
          <div key={r.label} className="flex flex-wrap gap-x-2">
            <dt className="text-dim">{r.label}:</dt>
            <dd className="text-ink">{r.value}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}

function UnfinishedInterviewNotice({ to }: { to: string }) {
  return (
    <div className="border-line bg-card rounded-xl border px-6 py-5.5">
      <p className="text-ink m-0 text-[15px] font-semibold">
        По этой вакансии уже есть незавершённое интервью
      </p>
      <p className="text-muted mt-1.5 text-[13.5px]">
        Новое можно начать, когда закончите текущее — вопросы уже готовы,
        продолжите с того места, где остановились.
      </p>
      <Link to={to} className={buttonClasses({ className: 'mt-4' })}>
        Продолжить интервью
      </Link>
    </div>
  )
}

function InterviewForm() {
  const navigate = useNavigate()
  const create = useCreateInterview()

  const [url, setUrl] = useState(() => takePendingVacancyUrl())
  const trimmed = url.trim()
  const debouncedUrl = useDebounced(trimmed)
  const validUrl = isHhVacancyUrl(trimmed)

  const preview = useVacancyPreview(debouncedUrl)

  const conflictVacancyId =
    create.error instanceof ApiRequestError && create.error.status === 409
      ? hhVacancyId(trimmed)
      : ''
  const conflictVacancy = useInterviewVacancy(
    conflictVacancyId,
    conflictVacancyId !== '',
  )
  const pending =
    conflictVacancy.data?.interviews.filter((i) => i.status !== 'COMPLETED') ??
    []
  const unfinished = pending[pending.length - 1]

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!validUrl || create.isPending) return
    create.mutate(
      { vacancyUrl: trimmed },
      {
        onSuccess: (session) =>
          navigate(`/app/interview/${session.id}`, { replace: true }),
      },
    )
  }

  const onUrlChange = (value: string) => {
    setUrl(value)
    if (create.isError) create.reset()
  }

  const previewErrorText =
    preview.isError && isHhVacancyUrl(debouncedUrl)
      ? interviewCreateErrorMessage(preview.error)
      : null

  return (
    <form onSubmit={onSubmit} className="mt-10 max-w-160 space-y-4.5">
      {conflictVacancyId ? (
        <UnfinishedInterviewNotice
          to={
            unfinished
              ? `/app/interview/${unfinished.sessionId}`
              : `/app/interview/vacancy/${conflictVacancyId}`
          }
        />
      ) : (
        create.isError && (
          <Alert>{interviewCreateErrorMessage(create.error)}</Alert>
        )
      )}

      <Field
        label="Ссылка на вакансию hh.ru"
        hint="Например: https://hh.ru/vacancy/123456"
        placeholder="https://hh.ru/vacancy/123456"
        type="url"
        inputMode="url"
        autoComplete="off"
        value={url}
        onChange={(e) => onUrlChange(e.target.value)}
        required
      />

      {trimmed !== '' && !validUrl && (
        <p className="text-dim text-[12.5px]">
          Вставьте прямую ссылку на вакансию hh.ru вида
          https://hh.ru/vacancy/123456.
        </p>
      )}

      {validUrl && preview.isLoading && (
        <div
          role="status"
          className="border-line bg-card rounded-xl border px-6 py-5.5"
        >
          <span className="sr-only">Загрузка вакансии…</span>
          <Skeleton className="h-3 w-20" />
          <Skeleton className="mt-3 h-6 w-2/3" />
          <Skeleton className="mt-4 h-4 w-40" />
        </div>
      )}

      {previewErrorText && <Alert>{previewErrorText}</Alert>}

      {preview.data && <PreviewCard preview={preview.data} />}

      <div>
        <Button
          type="submit"
          disabled={
            !validUrl ||
            preview.isError ||
            create.isPending ||
            conflictVacancyId !== ''
          }
        >
          {create.isPending ? 'Готовим вопросы…' : 'Начать интервью'}
        </Button>
        <p className="text-dim mt-3 text-[12.5px]">
          Рецензент прочитает вакансию и составит вопросы под неё — это займёт
          несколько секунд.
        </p>
      </div>
    </form>
  )
}

export function NewInterviewPage() {
  usePageTitle('Новое интервью')

  return (
    <Container>
      <AppPageHeader
        back={{ to: '/app/interview', label: 'Интервью' }}
        eyebrow="Новое интервью"
        title="Интервью под вакансию"
      >
        Вставьте ссылку на вакансию с hh.ru. Рецензент подберёт вопросы под её
        требования, а в конце разберёт ваши ответы и оценит шансы на оффер.
      </AppPageHeader>

      <InterviewForm />
    </Container>
  )
}
