import { useEffect, useRef, useState } from 'react'

/** Появление при скролле: как только элемент въезжает во вьюпорт, `shown`
 *  становится true (один раз, потом наблюдатель отключается). Вешать вместе с
 *  `animate-rise`/`opacity-0`. reduced-motion гасится глобальным CSS — анимация
 *  просто становится мгновенной, контент не остаётся скрытым. */
export function useReveal<T extends HTMLElement = HTMLElement>() {
  const ref = useRef<T>(null)
  const [shown, setShown] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          setShown(true)
          io.disconnect()
        }
      },
      { threshold: 0.15, rootMargin: '0px 0px -8% 0px' },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [])

  return { ref, shown }
}
