import { useReducedMotion } from 'motion/react'
import { motionTokens } from '@/lib/motion'

export function useSafeMotion(distance: number = motionTokens.distance.md) {
  const reduce = useReducedMotion()
  return {
    initial: { opacity: 0, y: reduce ? 0 : distance },
    animate: { opacity: 1, y: 0 },
    exit: { opacity: 0, y: reduce ? 0 : -distance },
  }
}
