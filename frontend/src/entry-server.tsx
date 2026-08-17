import { renderToString } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  createStaticHandler,
  createStaticRouter,
  StaticRouterProvider,
} from 'react-router-dom'
import { marketingRoute } from '@/app/marketingRoutes'
import { RootLayout } from '@/components/layout/RootLayout'
import { SITE } from '@/content/seo'

const { query, dataRoutes } = createStaticHandler([
  { element: <RootLayout />, children: [marketingRoute] },
])

export async function render(path: string): Promise<string> {
  const context = await query(new Request(`${SITE}${path}`))
  if (context instanceof Response) {
    throw new Error(`Unexpected response while rendering ${path}`)
  }
  const router = createStaticRouter(dataRoutes, context)
  return renderToString(
    <QueryClientProvider client={new QueryClient()}>
      <StaticRouterProvider router={router} context={context} hydrate={false} />
    </QueryClientProvider>,
  )
}

export { seoPages, notFoundSeo, SITE } from '@/content/seo'
