import { IconMoon, IconSun } from '@/components/marketing/icons'
import { cn } from '@/lib/cn'
import { useTheme, type Theme } from '@/lib/useTheme'

const OPTIONS: { value: Theme; label: string; Icon: typeof IconSun }[] = [
  { value: 'light', label: 'Светлая', Icon: IconSun },
  { value: 'dark', label: 'Тёмная', Icon: IconMoon },
]

export function ThemeSwitch({ className }: { className?: string }) {
  const { theme, toggle } = useTheme()
  return (
    <div
      role="radiogroup"
      aria-label="Тема оформления"
      className={cn(
        'bg-glass grid grid-cols-2 gap-1 rounded-xl p-1',
        className,
      )}
    >
      {OPTIONS.map(({ value, label, Icon }) => {
        const active = theme === value
        return (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => !active && toggle()}
            className={cn(
              'flex min-h-11 touch-manipulation items-center justify-center gap-2 rounded-lg text-sm font-medium transition-colors lg:min-h-9',
              active
                ? 'bg-indigo/15 text-ink ring-indigo/35 ring-1'
                : 'text-muted hover:text-ink',
            )}
          >
            <Icon className="size-[18px]" />
            {label}
          </button>
        )
      })}
    </div>
  )
}
