import { useRef, useState, type ChangeEvent, type ReactNode } from 'react'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Container } from '@/components/ui/Container'
import { Skeleton } from '@/components/ui/Skeleton'
import { Spinner } from '@/components/ui/Spinner'
import type { Resume } from '@/features/resume/api'
import { resumeErrorMessage } from '@/features/resume/errors'
import { ResumePreviewDialog } from '@/features/resume/ResumePreviewDialog'
import { ResumeRenameDialog } from '@/features/resume/ResumeRenameDialog'
import {
  MAX_RESUMES,
  RESUME_ACCEPT,
  resumeFileError,
} from '@/features/resume/files'
import {
  useDeleteResume,
  useDownloadResume,
  useResumes,
  useUploadResume,
} from '@/features/resume/useResumes'
import { getErrorMessage } from '@/lib/api'
import { formatDate } from '@/lib/dates'
import { usePageTitle } from '@/lib/usePageTitle'

export function ResumeListPage() {
  usePageTitle('Мои резюме')
  const { data: resumes, isLoading, isError, error } = useResumes()
  const upload = useUploadResume()
  const hasResumes = !!resumes?.length
  const atLimit = (resumes?.length ?? 0) >= MAX_RESUMES
  const inputRef = useRef<HTMLInputElement>(null)
  const [fileError, setFileError] = useState<string | null>(null)

  const pickFile = () => inputRef.current?.click()

  const onFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    const error = resumeFileError(file)
    setFileError(error)
    if (error) {
      upload.reset()
      return
    }
    upload.mutate(file)
  }

  return (
    <Container>
      <AppPageHeader
        back={{ to: '/', label: 'Главная' }}
        eyebrow="Резюме"
        title="Мои резюме"
        actions={
          hasResumes && (
            <UploadButton
              pending={upload.isPending}
              disabled={atLimit}
              onClick={pickFile}
            />
          )
        }
      />

      <input
        ref={inputRef}
        type="file"
        accept={RESUME_ACCEPT}
        className="hidden"
        onChange={onFileChange}
      />

      <div className="mt-8 flex flex-col gap-4">
        {atLimit && (
          <p className="text-muted text-sm">
            У тебя {MAX_RESUMES} резюме. Удали одно, чтобы загрузить новое.
          </p>
        )}

        {fileError && <Alert>{fileError}</Alert>}
        {upload.isError && <Alert>{resumeErrorMessage(upload.error)}</Alert>}

        {isLoading && <ResumeListSkeleton />}

        {isError && <Alert>{getErrorMessage(error)}</Alert>}

        {resumes && resumes.length === 0 && (
          <EmptyState pending={upload.isPending} onUpload={pickFile} />
        )}

        {resumes && hasResumes && (
          <ul className="flex flex-col gap-4">
            {resumes.map((r) => (
              <ResumeCard key={r.id} resume={r} />
            ))}
          </ul>
        )}
      </div>
    </Container>
  )
}

function UploadButton({
  pending,
  disabled,
  onClick,
  size,
}: {
  pending: boolean
  disabled?: boolean
  onClick: () => void
  size?: 'lg'
}) {
  return (
    <Button size={size} onClick={onClick} disabled={pending || disabled}>
      {pending ? (
        <>
          <Spinner className="size-4 border-white" />
          Загружаем…
        </>
      ) : (
        'Загрузить резюме'
      )}
    </Button>
  )
}

function ResumeListSkeleton() {
  return (
    <div role="status" className="flex flex-col gap-4">
      <span className="sr-only">Загрузка списка резюме…</span>
      {[0, 1].map((i) => (
        <div key={i} className="border-line bg-card rounded-xl border p-6">
          <Skeleton className="h-5 w-48" />
          <Skeleton className="mt-3 h-4 w-32" />
        </div>
      ))}
    </div>
  )
}

function EmptyState({
  pending,
  onUpload,
}: {
  pending: boolean
  onUpload: () => void
}) {
  return (
    <div className="border-line rounded-xl border border-dashed p-10 text-center">
      <h2 className="text-ink text-xl font-bold">Пока нет резюме</h2>
      <p className="text-muted mx-auto mt-2 max-w-md text-sm">
        Загрузи резюме в PDF, DOCX или TXT. Оно будет храниться здесь, и видишь
        его только ты.
      </p>
      <div className="mt-6">
        <UploadButton pending={pending} onClick={onUpload} size="lg" />
      </div>
    </div>
  )
}

function ResumeCard({ resume }: { resume: Resume }) {
  const del = useDeleteResume()
  const download = useDownloadResume()
  const [confirming, setConfirming] = useState(false)
  const [previewing, setPreviewing] = useState(false)
  const [renaming, setRenaming] = useState(false)
  const error = del.error ?? download.error

  const onDelete = () => {
    setConfirming(false)
    del.mutate(resume.id)
  }

  return (
    <li className="border-line bg-card flex flex-wrap justify-between gap-5 rounded-xl border px-6 py-5.5">
      <div className="min-w-0">
        <h2 className="text-ink text-[17px] font-semibold tracking-[-0.01em] break-words">
          {resume.name}
        </h2>
        <div className="text-dim mt-2 flex flex-wrap items-center gap-x-3.5 gap-y-1.5 text-[12.5px]">
          <span>{resume.format}</span>
          <span>{formatDate(resume.uploadedAt)}</span>
        </div>
      </div>

      <div className="flex flex-col items-end justify-center gap-2.5">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          <CardAction onClick={() => setPreviewing(true)}>Просмотр</CardAction>
          <CardAction
            onClick={() => download.mutate(resume)}
            disabled={download.isPending}
          >
            Скачать
          </CardAction>
          <CardAction onClick={() => setRenaming(true)}>
            Переименовать
          </CardAction>
          <CardAction
            onClick={() => setConfirming(true)}
            disabled={del.isPending}
          >
            Удалить
          </CardAction>
        </div>
        {error && (
          <p className="text-danger text-[12.5px]">
            {resumeErrorMessage(error)}
          </p>
        )}
      </div>

      <ResumePreviewDialog
        resume={resume}
        open={previewing}
        onClose={() => setPreviewing(false)}
      />
      <ResumeRenameDialog
        resume={resume}
        open={renaming}
        onClose={() => setRenaming(false)}
      />
      <ConfirmDialog
        open={confirming}
        title="Удалить резюме?"
        text={`Резюме «${resume.name}» будет удалено вместе с файлом.`}
        onConfirm={onDelete}
        onClose={() => setConfirming(false)}
      />
    </li>
  )
}

function CardAction({
  onClick,
  disabled,
  children,
}: {
  onClick: () => void
  disabled?: boolean
  children: ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="text-dim hover:text-ink text-[13px] transition-colors disabled:opacity-50"
    >
      {children}
    </button>
  )
}
