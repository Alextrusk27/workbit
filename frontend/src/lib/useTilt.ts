import { useEffect, useRef } from 'react'

/** «Приподнятый лист»: элемент наклоняется к курсору (CSS 3D), а на нём и его
 *  потомках выставляются переменные для света и параллакса:
 *    --rx/--ry — угол наклона; --mx/--my — позиция блика; --px/--py — сдвиг
 *    курсора от центра (−0.5..0.5) для параллакса слоёв; --active — 0/1.
 *  Значения пишутся напрямую в style (без ре-рендеров), обновление — в rAF.
 *  Отключается при prefers-reduced-motion и на грубом указателе (тач). */
export function useTilt<T extends HTMLElement = HTMLElement>(max = 6) {
  const ref = useRef<T>(null)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const noMotion = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    ).matches
    const coarse = window.matchMedia('(pointer: coarse)').matches
    if (noMotion || coarse) return

    let raf = 0
    const onMove = (e: PointerEvent) => {
      const r = el.getBoundingClientRect()
      const px = (e.clientX - r.left) / r.width - 0.5
      const py = (e.clientY - r.top) / r.height - 0.5
      cancelAnimationFrame(raf)
      raf = requestAnimationFrame(() => {
        el.style.setProperty('--rx', `${-py * max}deg`)
        el.style.setProperty('--ry', `${px * max}deg`)
        el.style.setProperty('--mx', `${(px + 0.5) * 100}%`)
        el.style.setProperty('--my', `${(py + 0.5) * 100}%`)
        el.style.setProperty('--px', `${px}`)
        el.style.setProperty('--py', `${py}`)
        el.style.setProperty('--active', '1')
      })
    }
    const onLeave = () => {
      cancelAnimationFrame(raf)
      el.style.setProperty('--rx', '0deg')
      el.style.setProperty('--ry', '0deg')
      el.style.setProperty('--px', '0')
      el.style.setProperty('--py', '0')
      el.style.setProperty('--active', '0')
    }
    el.addEventListener('pointermove', onMove)
    el.addEventListener('pointerleave', onLeave)
    return () => {
      cancelAnimationFrame(raf)
      el.removeEventListener('pointermove', onMove)
      el.removeEventListener('pointerleave', onLeave)
    }
  }, [max])

  return ref
}
