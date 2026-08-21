import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { motion, useReducedMotion } from 'motion/react'
import { motionTokens } from '@/lib/motion'

/** На пререндеренной странице разметка уже на экране: прятать её при гидратации
 *  нельзя — контент мигает и прыгает. Флаг взводится на сервере и при гидратации
 *  непустого root, а после первой отрисовки сбрасывается, чтобы SPA-переходы
 *  анимировались как раньше. */
let prerenderPending =
  typeof document === 'undefined' ||
  (document.getElementById('root')?.childElementCount ?? 0) > 0

if (typeof document !== 'undefined' && prerenderPending) {
  requestAnimationFrame(() =>
    requestAnimationFrame(() => {
      prerenderPending = false
    }),
  )
}

/** Появление блока при прокрутке — однократное, без повторов на скролл-аут. */
export function Reveal({
  children,
  delay = 0,
  className,
}: {
  children: ReactNode
  delay?: number
  className?: string
}) {
  const reduce = useReducedMotion()
  const ref = useRef<HTMLDivElement>(null)
  const [asIs, setAsIs] = useState(() => prerenderPending)

  // Блоки ниже первого экрана невидимы, их можно вернуть в анимацию до отрисовки.
  useLayoutEffect(() => {
    if (!asIs) return
    const el = ref.current
    if (el && el.getBoundingClientRect().top > window.innerHeight) {
      setAsIs(false)
    }
  }, [asIs])

  return (
    <motion.div
      ref={ref}
      className={className}
      initial={
        asIs || reduce ? false : { opacity: 0, y: motionTokens.distance.lg }
      }
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '-80px' }}
      transition={{
        duration: motionTokens.duration.slow,
        ease: motionTokens.easing.smooth,
        delay,
      }}
    >
      {children}
    </motion.div>
  )
}
