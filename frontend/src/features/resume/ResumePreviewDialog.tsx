import { useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { IconClose } from '@/components/marketing/icons'
import { Alert } from '@/components/ui/Alert'
import { buttonClasses } from '@/components/ui/buttonStyles'
import {
  modalOverlayClasses,
  modalShellClasses,
} from '@/components/ui/modalStyles'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { motionTokens } from '@/lib/motion'
import { useModalA11y } from '@/lib/useModalA11y'
import type { Resume } from './api'
import { resumeErrorMessage } from './errors'
import { decodeText } from './preview'
import { useResumeFile } from './useResumes'

const RENDER_FAILED = 'Не удалось показать файл. Скачай его, чтобы открыть.'

/** Предпросмотр исходного файла. Файл берём через `apiFetchBlob` и показываем
 *  по blob-URL: API отдаёт `X-Frame-Options: DENY`, `<iframe src>` на него не
 *  откроется. */
export function ResumePreviewDialog({
  resume,
  open,
  onClose,
}: {
  resume: Resume
  open: boolean
  onClose: () => void
}) {
  const panelRef = useModalA11y(open)

  useEffect(() => {
    if (!open) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: motionTokens.duration.fast }}
          className={cn(
            modalOverlayClasses,
            'flex items-center justify-center',
          )}
          onClick={(e) => {
            if (e.target === e.currentTarget) onClose()
          }}
        >
          <motion.div
            initial={{ opacity: 0, y: motionTokens.distance.sm, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: motionTokens.distance.sm, scale: 0.98 }}
            transition={{
              duration: motionTokens.duration.fast,
              ease: motionTokens.easing.smooth,
            }}
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-label={resume.name}
            className={cn(
              modalShellClasses,
              'flex h-full w-full max-w-4xl flex-col overflow-hidden',
            )}
          >
            <div className="border-line flex items-center justify-between gap-4 border-b py-2 pr-2 pl-5">
              <h3 className="text-ink truncate text-[15px] font-semibold">
                {resume.name}
              </h3>
              <button
                type="button"
                onClick={onClose}
                aria-label="Закрыть"
                className="text-muted hover:text-ink hover:bg-indigo/8 inline-flex size-9 shrink-0 items-center justify-center rounded-md transition-colors"
              >
                <IconClose className="size-5" />
              </button>
            </div>

            <div className="min-h-0 flex-1 overflow-auto">
              <PreviewBody resume={resume} />
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

/** Смонтирован только пока модалка открыта: с `gcTime: 0` blob уходит из кэша
 *  при закрытии, и каждое открытие берёт файл заново. */
function PreviewBody({ resume }: { resume: Resume }) {
  const file = useResumeFile(resume.id)

  if (file.isPending) {
    return (
      <div className="flex h-full items-center justify-center">
        <Spinner />
      </div>
    )
  }
  if (file.isError) {
    return (
      <div className="p-5">
        <Alert>{resumeErrorMessage(file.error)}</Alert>
      </div>
    )
  }
  return (
    <FilePreview format={resume.format} blob={file.data} title={resume.name} />
  )
}

function FilePreview({
  format,
  blob,
  title,
}: {
  format: Resume['format']
  blob: Blob
  title: string
}) {
  switch (format) {
    case 'PDF':
      return <PdfPreview blob={blob} title={title} />
    case 'TXT':
      return <TxtPreview blob={blob} />
    case 'DOCX':
      return <DocxPreview blob={blob} />
  }
}

/** iOS Safari показывает в iframe только первую страницу PDF, поэтому на
 *  мобильном есть ссылка на тот же blob-URL в новой вкладке. */
function PdfPreview({ blob, title }: { blob: Blob; title: string }) {
  const frameRef = useRef<HTMLIFrameElement>(null)
  const linkRef = useRef<HTMLAnchorElement>(null)

  useEffect(() => {
    const url = URL.createObjectURL(blob)
    if (frameRef.current) frameRef.current.src = url
    if (linkRef.current) linkRef.current.href = url
    return () => URL.revokeObjectURL(url)
  }, [blob])

  return (
    <div className="flex h-full flex-col">
      <div className="border-line border-b p-3 sm:hidden">
        <a
          ref={linkRef}
          target="_blank"
          rel="noopener"
          className={buttonClasses({
            variant: 'secondary',
            size: 'sm',
            className: 'w-full',
          })}
        >
          Открыть в новой вкладке
        </a>
      </div>
      <iframe ref={frameRef} title={title} className="min-h-0 w-full flex-1" />
    </div>
  )
}

function TxtPreview({ blob }: { blob: Blob }) {
  const [text, setText] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    decodeText(blob).then(
      (decoded) => {
        if (!cancelled) setText(decoded)
      },
      () => {
        if (!cancelled) setFailed(true)
      },
    )
    return () => {
      cancelled = true
    }
  }, [blob])

  if (failed) return <RenderFailed />
  return (
    <pre className="text-ink p-5 font-mono text-[13px] leading-[1.6] break-words whitespace-pre-wrap">
      {text}
    </pre>
  )
}

/** `docx-preview` тяжёлый, грузим его только при открытии DOCX. Рендер
 *  приблизительный: сложная вёрстка может поехать, скачивание остаётся. */
function DocxPreview({ blob }: { blob: Blob }) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'failed'>('loading')

  useEffect(() => {
    let cancelled = false
    import('docx-preview')
      .then(async ({ parseAsync, renderDocument }) => {
        const nodes = await renderDocument(await parseAsync(blob))
        if (cancelled || !containerRef.current) return
        containerRef.current.replaceChildren(...nodes)
        setState('ready')
      })
      .catch(() => {
        if (!cancelled) setState('failed')
      })
    return () => {
      cancelled = true
    }
  }, [blob])

  return (
    <>
      {state === 'loading' && (
        <div className="flex h-full items-center justify-center">
          <Spinner />
        </div>
      )}
      {state === 'failed' && <RenderFailed />}
      <div ref={containerRef} className={state === 'ready' ? '' : 'hidden'} />
    </>
  )
}

function RenderFailed() {
  return (
    <div className="p-5">
      <Alert>{RENDER_FAILED}</Alert>
    </div>
  )
}
