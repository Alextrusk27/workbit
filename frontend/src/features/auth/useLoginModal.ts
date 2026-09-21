import { createContext, useContext } from 'react'

export interface LoginModalOptions {
  from?: string
  onSuccess?: () => void
}

export const LoginModalContext = createContext<
  (options?: LoginModalOptions) => void
>(() => {})

export function useLoginModal() {
  return useContext(LoginModalContext)
}
