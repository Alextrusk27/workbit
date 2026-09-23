import type { ComponentType } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import {
  IconChat,
  IconChevronRight,
  IconLogout,
  IconPencil,
  IconUser,
  IconSettings,
} from '@/components/marketing/icons'
import { ThemeSwitch } from '@/components/ui/ThemeSwitch'
import { useAuth, useLogout } from '@/features/auth/useAuth'
import { cn } from '@/lib/cn'
import { motionTokens, springs } from '@/lib/motion'

const rowClass =
  'text-ink hover:bg-glass flex min-h-11 w-full items-center gap-3 rounded-lg px-3 text-left text-[15px] transition-colors lg:min-h-10 lg:text-sm'

const links: {
  to: string
  label: string
  Icon: ComponentType<{ className?: string }>
}[] = [
  { to: '/app/interview', label: 'Мои интервью', Icon: IconChat },
  { to: '/app/training', label: 'Мои тренировки', Icon: IconPencil },
  { to: '/app/settings', label: 'Настройки', Icon: IconSettings },
]

interface UserMenuProps {
  open: boolean
  onToggle: () => void
  onClose: () => void
}

export function UserMenu({ open, onToggle, onClose }: UserMenuProps) {
  const { user } = useAuth()
  const logout = useLogout()
  const navigate = useNavigate()
  const reduce = useReducedMotion()

  if (!user) return null

  // Навигация ДО logout: сброс ['me'] иначе перекинет RequireAuth на /login.
  const onLogout = () => {
    onClose()
    navigate('/', { replace: true })
    logout.mutate()
  }

  return (
    <div className="shrink-0 lg:relative">
      <button
        type="button"
        onClick={onToggle}
        aria-haspopup="true"
        aria-expanded={open}
        aria-controls="user-menu"
        aria-label="Меню пользователя"
        className={cn(
          'avatar flex size-11 touch-manipulation items-center justify-center rounded-full transition hover:brightness-110 sm:size-9',
          open && 'ring-violet/35 ring-4',
        )}
      >
        <IconUser className="size-5 sm:size-4" />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            key="user-menu"
            id="user-menu"
            className="border-line bg-pop shadow-pop absolute inset-x-0 top-full z-50 mt-2 rounded-2xl border p-2 lg:inset-x-auto lg:right-0 lg:w-72"
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
            <div className="flex items-center gap-3 px-3 pt-2 pb-3">
              <span className="avatar flex size-10 shrink-0 items-center justify-center rounded-full">
                <IconUser className="size-5" />
              </span>
              <span className="min-w-0">
                <span className="text-dim block text-xs">Ты вошёл как</span>
                <span className="text-ink block truncate text-sm font-medium">
                  {user.email}
                </span>
              </span>
            </div>

            {links.map(({ to, label, Icon }) => (
              <Link key={to} to={to} onClick={onClose} className={rowClass}>
                <Icon className="text-muted size-5 shrink-0" />
                <span className="flex-1">{label}</span>
                <IconChevronRight className="text-dim size-4 shrink-0" />
              </Link>
            ))}
            <button
              type="button"
              onClick={onLogout}
              disabled={logout.isPending}
              className={cn(rowClass, 'disabled:opacity-50')}
            >
              <IconLogout className="text-muted size-5 shrink-0" />
              {logout.isPending ? 'Выходим…' : 'Выйти'}
            </button>

            <ThemeSwitch className="mt-2" />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
