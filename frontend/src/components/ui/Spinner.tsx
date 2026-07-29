import { motion, useReducedMotion } from 'motion/react'
import { motionTokens } from '@/lib/motion'
import { cn } from '@/lib/cn'

/** Кольцевой индикатор ожидания ответа модели. */
export function Spinner({ className }: { className?: string }) {
  const reduce = useReducedMotion()
  return (
    <motion.span
      aria-hidden
      className={cn(
        'border-indigo inline-block size-[18px] rounded-full border-2 border-t-transparent align-[-3px]',
        className,
      )}
      animate={reduce ? undefined : { rotate: 360 }}
      transition={{
        duration: motionTokens.duration.crawl,
        repeat: Infinity,
        ease: 'linear',
      }}
    />
  )
}
