import { createContext, useContext } from 'react'

export const TopUpModalContext = createContext<(limits?: number) => void>(
  () => {},
)

export function useTopUpModal() {
  return useContext(TopUpModalContext)
}
