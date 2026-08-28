import { QueryClient } from '@tanstack/react-query'
import { ApiRequestError } from '@/lib/api'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (failureCount, error) =>
        failureCount < 1 &&
        !(error instanceof ApiRequestError && error.status < 500),
      refetchOnWindowFocus: false,
    },
  },
})
