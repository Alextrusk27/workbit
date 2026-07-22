import type { FormEvent } from 'react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import { Field } from '@/components/ui/Field'
import { Skeleton } from '@/components/ui/Skeleton'
import type { VacancyPreview } from '@/features/vacancy/api'
import {
  isHhVacancyUrl,
  useVacancyPreview,
} from '@/features/vacancy/useVacancy'
import { useCreateInterview } from '@/features/interview/useInterview'
import { interviewCreateErrorMessage } from '@/features/interview/errors'
import { useDebounced } from '@/lib/useDebounced'
import { usePageTitle } from '@/lib/usePageTitle'

function PreviewCard({ preview }: { preview: VacancyPreview }) {
  const rows = [
    { label: 'Работодатель', value: preview.employer },
    { label: 'Зарплата', value: preview.salary },
    { label: 'Опыт', value: preview.experience },
  ].filter((r) => r.value)

  return (
    <div className="border-rule bg-paper-2/60 animate-rise rounded-lg border p-5">
      <p className="text-muted font-mono text-xs tracking-[0.15em] uppercase">
        Вакансия
      </p>
      <h2 className="text-ink font-display mt-2 text-xl break-words">
        {preview.name}
      </h2>
      <dl className="mt-4 space-y-1.5 text-sm">
        {rows.map((r) => (
          <div key={r.label} className="flex flex-wrap gap-x-2">
            <dt className="text-muted">{r.label}:</dt>
            <dd className="text-ink">{r.value}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}

function InterviewForm() {
  const navigate = useNavigate()
  const create = useCreateInterview()

  const [url, setUrl] = useState('')
  const trimmed = url.trim()
  const debouncedUrl = useDebounced(trimmed)
  const validUrl = isHhVacancyUrl(trimmed)

  const preview = useVacancyPreview(debouncedUrl)

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

  const previewErrorText =
    preview.isError && isHhVacancyUrl(debouncedUrl)
      ? interviewCreateErrorMessage(preview.error)
      : null

  return (
    <form onSubmit={onSubmit} className="mt-8 space-y-6">
      {create.isError && (
        <Alert>{interviewCreateErrorMessage(create.error)}</Alert>
      )}

      <Field
        label="Ссылка на вакансию hh.ru"
        hint="Например: https://hh.ru/vacancy/123456"
        placeholder="https://hh.ru/vacancy/123456"
        type="url"
        inputMode="url"
        autoComplete="off"
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        required
      />

      {trimmed !== '' && !validUrl && (
        <p className="text-muted text-xs">
          Вставьте прямую ссылку на вакансию hh.ru вида
          https://hh.ru/vacancy/123456.
        </p>
      )}

      {validUrl && preview.isLoading && (
        <div role="status" className="border-rule rounded-lg border p-5">
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
          size="lg"
          disabled={!validUrl || preview.isError || create.isPending}
        >
          {create.isPending ? 'Готовим вопросы…' : 'Начать интервью'}
        </Button>
        <p className="text-muted mt-3 text-xs">
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
    <Container className="py-12 sm:py-16">
      <Link
        to="/app/interview"
        className="text-accent hover:text-accent-hover text-sm transition-colors"
      >
        ← Интервью
      </Link>
      <p className="text-muted mt-8 font-mono text-xs tracking-[0.2em] uppercase">
        Новое интервью
      </p>
      <h1 className="text-ink mt-4 text-3xl sm:text-4xl">
        Интервью под вакансию
      </h1>
      <p className="text-muted mt-4 max-w-xl">
        Вставьте ссылку на вакансию с hh.ru. Рецензент подберёт вопросы под её
        требования, а в конце разберёт ваши ответы и оценит шансы на оффер.
      </p>

      <div className="mt-10 max-w-2xl">
        <InterviewForm />
      </div>
    </Container>
  )
}
