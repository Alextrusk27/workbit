import { cn } from '@/lib/cn'

interface LogoProps {
  className?: string
}

export function Logo({ className }: LogoProps) {
  return (
    <span
      translate="no"
      className={cn(
        'text-ink text-[26px] leading-none font-extrabold tracking-[-0.02em]',
        className,
      )}
    >
      work<span className="text-grad">bit.</span>
    </span>
  )
}
