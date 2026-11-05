/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */

// Writes dist/THIRD-PARTY-LICENSES.txt with the license texts of the npm packages whose code is in the bundle,
// plus the bundled Montserrat font. Runs after `vite build`.
import { existsSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const root = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
// packages whose code is in the bundle, recorded by the Vite plugin in vite.config.ts
const bundled = JSON.parse(readFileSync(join(root, 'dist', 'bundled-packages.json'), 'utf8'))
const packages = new Map()
for (const name of bundled) {
  const pkg = JSON.parse(readFileSync(join(root, 'node_modules', name, 'package.json'), 'utf8'))
  packages.set(name, { name, version: pkg.version })
}

function licenseText(dir) {
  const file = readdirSync(dir).find((f) => /^(licen[cs]e|copying)(\.(md|txt))?$/i.test(f))
  return file ? readFileSync(join(dir, file), 'utf8').trim() : null
}

const parts = [
  'Third-party software contained in the JEVis web app',
  '=====================================================',
  '',
  'The JEVis web app is licensed under the GNU General Public License version 3 only.',
  'It contains the following third-party components, under their own licenses:',
  '',
]
const missing = []
for (const { name, version } of [...packages.values()].sort((a, b) => a.name.localeCompare(b.name))) {
  const dir = join(root, 'node_modules', name)
  const pkg = JSON.parse(readFileSync(join(dir, 'package.json'), 'utf8'))
  const text = licenseText(dir)
  if (!text) missing.push(name)
  parts.push('-'.repeat(78), `${name} ${version} (${pkg.license ?? 'unknown'})`, pkg.homepage ?? '', '', text ?? `License: ${pkg.license}`, '')
}
const ofl = join(root, 'public', 'fonts', 'Montserrat-OFL.txt')
if (existsSync(ofl)) {
  parts.push('-'.repeat(78), 'Montserrat font (SIL Open Font License 1.1)', '', readFileSync(ofl, 'utf8').trim(), '')
}
writeFileSync(join(root, 'dist', 'THIRD-PARTY-LICENSES.txt'), parts.join('\n'))
rmSync(join(root, 'dist', 'bundled-packages.json'))
console.log(`THIRD-PARTY-LICENSES.txt: ${packages.size} packages${missing.length ? ', no license file: ' + missing.join(', ') : ''}`)
