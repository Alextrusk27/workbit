import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
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
    <div ref={ref} className="relative max-w-[50vw] min-w-0 sm:max-w-55">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="true"
        aria-expanded={open}
        className="text-muted hover:bg-glass hover:text-ink flex max-w-full touch-manipulation items-center gap-1.5 rounded-md px-2.5 py-2 text-sm font-medium transition-colors"
      >
        <span className="min-w-0 truncate">{user.email}</span>
        <svg
          viewBox="0 0 12 12"
          className={`size-3 shrink-0 transition-transform ${open ? 'rotate-180' : ''}`}
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          aria-hidden="true"
        >
          <path d="M2.5 4.5 6 8l3.5-3.5" strokeLinecap="round" />
        </svg>
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            key="user-menu"
            className="border-line bg-pop shadow-pop absolute right-0 z-50 mt-2 w-50 rounded-lg border p-1.5"
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
            <Link
              to="/app"
              onClick={() => setOpen(false)}
              className={itemClass}
            >
              Рабочий стол
            </Link>
            <Link
              to="/app/settings"
              onClick={() => setOpen(false)}
              className={itemClass}
            >
              Аккаунт
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
