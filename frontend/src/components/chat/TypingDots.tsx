import { motion, useReducedMotion } from 'motion/react'
import { motionTokens } from '@/lib/motion'

/** Индикатор «собеседник печатает». */
export function TypingDots() {
  const reduce = useReducedMotion()
  return (
    <span className="inline-flex gap-1 py-1.5" aria-label="Печатает">
      {[0, 1, 2].map((i) => (
        <motion.i
          key={i}
          className="size-1.5 rounded-full bg-current"
          animate={
            reduce
              ? { opacity: 0.6 }
              : { opacity: [0.25, 0.9, 0.25], y: [0, -2, 0] }
          }
          transition={{
            duration: motionTokens.duration.crawl,
            repeat: reduce ? 0 : Infinity,
            delay: i * 0.18,
            ease: motionTokens.easing.sharp,
          }}
        />
      ))}
    </span>
  )
}
