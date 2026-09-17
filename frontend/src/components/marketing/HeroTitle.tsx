import type { Hero } from '@/content/types'

export function HeroTitle({ hero }: { hero: Hero }) {
  const { lead, accent, breakBeforeAccent } = hero
  return (
    <>
      {lead}
      {accent && (
        <>
          {breakBeforeAccent ? <br /> : ' '}
          <span className="text-grad">{accent}</span>
        </>
      )}
    </>
  )
}
