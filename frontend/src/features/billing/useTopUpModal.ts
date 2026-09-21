import { createContext, useContext } from 'react'

export const TopUpModalContext = createContext<(limits?: number) => void>(
  () => {},
)

/** Открывает модалку пополнения; необязательный аргумент — предвыбранное число лимитов. */
export function useTopUpModal() {
  return useContext(TopUpModalContext)
}
