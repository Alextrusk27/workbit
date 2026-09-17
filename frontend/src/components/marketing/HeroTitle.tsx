import type { Hero } from '@/content/types'

/** Заголовок героя из контента: лид и градиентный акцент через пробел или перенос. */
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
