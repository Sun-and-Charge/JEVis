/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { useI18n } from 'vue-i18n'
import { WIDGET_TYPES } from '../../../dashboard/edit/typeEditors'

/** Name of a widget type (the label of WIDGET_TYPES; the id itself if there is no text). */
export function useTypeLabel(): (type: unknown) => string {
  const { t, te } = useI18n()
  return (type: unknown) => {
    const id = String(type ?? '')
    const entry = WIDGET_TYPES.find((x) => x.id === id)
    return entry && entry.labelKey && te(entry.labelKey) ? t(entry.labelKey) : id
  }
}

/** Whether a widget type has data rows (WIDGET_TYPES; unknown types: when the widget already has a data handler). */
export function typeHasRows(type: unknown, hasDataHandler: boolean): boolean {
  const entry = WIDGET_TYPES.find((x) => x.id === String(type ?? ''))
  return entry ? entry.hasRows : hasDataHandler
}
