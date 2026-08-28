import { defineConfig } from 'vitest/config'

export default defineConfig({
  define: {
    __BUILD_TS__: JSON.stringify(Date.now()),
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
