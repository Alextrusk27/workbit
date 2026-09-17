import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const { version } = JSON.parse(
  readFileSync(fileURLToPath(new URL('./package.json', import.meta.url)), 'utf-8'),
) as { version: string }

const appVersion = version.replace('-SNAPSHOT', '')
const appBuild = process.env.APP_BUILD ?? 'dev'

function commitTime(): number {
  try {
    const seconds = Number(
      execFileSync('git', ['log', '-1', '--format=%ct'], {
        encoding: 'utf8',
      }).trim(),
    )
    if (seconds > 0) return seconds * 1000
  } catch {}
  return Date.now()
}

const versionMeta: Plugin = {
  name: 'app-version-meta',
  transformIndexHtml: {
    order: 'pre',
    handler: () => [
      {
        tag: 'meta',
        attrs: { name: 'app-version', content: appVersion },
        injectTo: 'head' as const,
      },
      {
        tag: 'meta',
        attrs: { name: 'app-build', content: appBuild },
        injectTo: 'head' as const,
      },
    ],
  },
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), versionMeta],
  define: {
    __BUILD_TS__: JSON.stringify(commitTime()),
    __APP_VERSION__: JSON.stringify(appVersion),
  },
  server: {
    host: '127.0.0.1',
    fs: {
      allow: ['..'],
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@docs': fileURLToPath(new URL('../docs', import.meta.url)),
    },
  },
})
