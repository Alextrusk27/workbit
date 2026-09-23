import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { IconRole } from '@/components/marketing/icons'
import { useAuth, useLogout } from '@/features/auth/useAuth'
import { motionTokens, springs } from '@/lib/motion'

const itemClass =
  'text-ink hover:bg-glass block w-full rounded-sm px-3 py-2.5 text-left text-sm transition-colors'

export function UserMenu() {
  const { user } = useAuth()
  const logout = useLogout()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const reduce = useReducedMotion()

  useEffect(() => {
    if (!open) return
    const onDocClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDocClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDocClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (!user) return null

  // Навигация ДО logout: сброс ['me'] иначе перекинет RequireAuth на /login.
  const onLogout = () => {
    setOpen(false)
    navigate('/', { replace: true })
    logout.mutate()
  }

  return (
    <div ref={ref} className="relative shrink-0">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="true"
        aria-expanded={open}
        aria-label="Меню пользователя"
        className="text-muted hover:bg-glass hover:text-ink border-line flex size-10 touch-manipulation items-center justify-center rounded-full border transition-colors sm:size-9"
      >
        <IconRole className="size-[22px] sm:size-[18px]" />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            key="user-menu"
            className="border-line bg-pop shadow-pop absolute right-0 z-50 mt-2 w-56 rounded-lg border p-1.5"
            initial={
              reduce
                ? { opacity: 0 }
                : { opacity: 0, y: -motionTokens.distance.xs }
            }
            animate={{ opacity: 1, y: 0 }}
            exit={
              reduce
                ? { opacity: 0 }
                : { opacity: 0, y: -motionTokens.distance.xs }
            }
            transition={springs.instant}
          >
            <p className="text-dim truncate px-3 pt-1.5 pb-2 text-xs">
              {user.email}
            </p>
            <Link
              to="/app/interview"
              onClick={() => setOpen(false)}
              className={itemClass}
            >
              Мои интервью
            </Link>
            <Link
              to="/app/training"
              onClick={() => setOpen(false)}
              className={itemClass}
            >
              Мои тренировки
            </Link>
            <div className="border-divider my-1.5 border-t" />
            <Link
              to="/app/settings"
              onClick={() => setOpen(false)}
              className={itemClass}
            >
              Настройки
            </Link>
            <button
              type="button"
              onClick={onLogout}
              disabled={logout.isPending}
              className={`${itemClass} disabled:opacity-50`}
            >
              {logout.isPending ? 'Выходим…' : 'Выйти'}
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
