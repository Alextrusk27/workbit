import { createContext, useContext } from 'react'

export interface WelcomeState {
  open: boolean
  show: () => void
  dismiss: () => void
}

export const WelcomeContext = createContext<WelcomeState>({
  open: false,
  show: () => {},
  dismiss: () => {},
})

export function useWelcome() {
  return useContext(WelcomeContext)
}
