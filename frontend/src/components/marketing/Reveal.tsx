import type { ReactNode } from 'react'
import { motion, useReducedMotion } from 'motion/react'
import { motionTokens } from '@/lib/motion'

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
  return (
    <motion.div
      className={className}
      initial={
        reduce ? { opacity: 1 } : { opacity: 0, y: motionTokens.distance.lg }
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
