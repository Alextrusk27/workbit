import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

interface ContainerProps {
  children: ReactNode
  className?: string
}

/** Центральная колонка страницы с боковыми полями. */
export function Container({ children, className }: ContainerProps) {
  return (
    <div className={cn('mx-auto w-full max-w-320 px-5 sm:px-8', className)}>
      {children}
    </div>
  )
}
