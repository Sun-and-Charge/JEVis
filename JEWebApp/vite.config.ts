/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import type { Plugin } from 'vite'

/** Records which npm packages end up in the bundle (dist/bundled-packages.json, read by scripts/licenses.mjs). */
function bundledPackages(): Plugin {
  return {
    name: 'jevis-bundled-packages',
    apply: 'build',
    generateBundle(_options, bundle) {
      const names = new Set<string>()
      for (const chunk of Object.values(bundle)) {
        if (chunk.type !== 'chunk') continue
        for (const id of Object.keys(chunk.modules)) {
          const match = id.replace(/\\/g, '/').match(/node_modules\/((?:@[^/]+\/)?[^/]+)/)
          if (match) names.add(match[1])
        }
      }
      this.emitFile({ type: 'asset', fileName: 'bundled-packages.json', source: JSON.stringify([...names].sort(), null, 1) })
    },
  }
}

// The app is served by JEWebService at /app/ (config key <webapp>); in development `npm run dev`
// forwards the API calls to a local web service.
export default defineConfig({
  base: '/app/',
  plugins: [vue(), bundledPackages()],
  server: {
    port: 5173,
    proxy: {
      '/JEWebService': process.env.JEVIS_WS ?? 'http://127.0.0.1:8090',
    },
  },
  build: {
    outDir: 'dist',
    // no source maps in the shipped build
    sourcemap: false,
    // the analysis view (lazy loaded) contains the needed parts of Apache ECharts (~680 kB, ~230 kB gzip)
    chunkSizeWarningLimit: 800,
  },
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.test.ts'],
  },
})
