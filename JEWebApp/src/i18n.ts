/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { createI18n } from 'vue-i18n'
import de from './locales/de.json'
import en from './locales/en.json'

export const LOCALES = ['de', 'en'] as const
export type Locale = (typeof LOCALES)[number]
const KEY = 'jevis.locale'

export function initialLocale(stored: string | null, browser: readonly string[]): Locale {
  if (stored && (LOCALES as readonly string[]).includes(stored)) return stored as Locale
  for (const lang of browser) {
    const short = lang.toLowerCase().slice(0, 2)
    if ((LOCALES as readonly string[]).includes(short)) return short as Locale
  }
  return 'de'
}

function storedLocale(): string | null {
  try {
    return window.localStorage.getItem(KEY)
  } catch {
    return null
  }
}

export const i18n = createI18n({
  legacy: false,
  locale: initialLocale(storedLocale(), navigator.languages ?? [navigator.language]),
  fallbackLocale: 'de',
  messages: { de, en },
})

export function setLocale(locale: Locale): void {
  i18n.global.locale.value = locale
  document.documentElement.lang = locale
  try {
    window.localStorage.setItem(KEY, locale)
  } catch {
    /* only for this page */
  }
}
