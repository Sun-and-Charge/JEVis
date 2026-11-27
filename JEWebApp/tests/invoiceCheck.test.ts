/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { checkOptions, checkStatus, defaultInvoiceFilter, filterInvoices } from '../src/accounting/invoices'
import type { InvoiceCheckResult } from '../src/api/invoices'
import InvoiceCheckPanel from '../src/components/accounting/InvoiceCheckPanel.vue'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import { invoice } from './fixtures/accounting'
import { httpError } from './fixtures/edit'

/* Result of the automatic invoice check (R03) in the register; neutral placeholder data. */

const deviation: InvoiceCheckResult = {
  status: 'DEVIATION',
  checkedAt: '2026-11-04T08:00:00.000+01:00',
  contract: { id: 30, name: 'Strom Halle 3' },
  quantity: { expected: 41000, invoiced: 41250, difference: 250, deviationPercent: 0.61, tolerancePercent: 2, ok: true, unit: 'kWh' },
  amount: { expected: 11000, invoiced: 11550, difference: 550, deviationPercent: 5, tolerancePercent: 1, ok: false },
}

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
})

describe('invoice check in the register', () => {
  it('status, chips and filter', () => {
    const list = [
      invoice(1, { check: deviation }),
      invoice(2, { check: { status: 'OK' } }),
      invoice(3, { check: { status: 'NOT_CHECKABLE', reason: 'noContract' } }),
      invoice(4),
      invoice(5, { check: { status: 'DEVIATION' } }),
    ]
    expect(list.map(checkStatus)).toEqual(['DEVIATION', 'OK', 'NOT_CHECKABLE', 'NONE', 'DEVIATION'])
    expect(checkOptions(list)).toEqual([
      { id: 'DEVIATION', count: 2 },
      { id: 'NOT_CHECKABLE', count: 1 },
      { id: 'OK', count: 1 },
      { id: 'NONE', count: 1 },
    ])
    const filter = { ...defaultInvoiceFilter(), check: 'DEVIATION' as const }
    expect(filterInvoices(list, filter, 'de').map((i) => i.id)).toEqual([1, 5])
    expect(filterInvoices(list, defaultInvoiceFilter(), 'de')).toHaveLength(5)
  })

  it('panel shows the comparisons and checks again', async () => {
    const post = vi.fn(async () => ({ status: 'OK' }))
    Object.assign(useAuthStore().http, { post })
    const w = mount(InvoiceCheckPanel, { props: { invoice: invoice(1, { check: deviation, canCheck: true }), editable: true }, global: { plugins: [i18n] } })
    const text = w.text()
    expect(text).toContain('Abweichung')
    expect(text).toContain('Strom Halle 3')
    expect(w.find('.inv-check-quantity').text()).toContain('41.250 kWh')
    expect(w.find('.inv-check-quantity').text()).toContain('In Ordnung')
    expect(w.find('.inv-check-amount').text()).toContain('550 € (5 %)')
    expect(w.find('.inv-check-amount').text()).toContain('± 1 %')
    await w.find('.inv-recheck').trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/invoices/1/check')
    expect(w.emitted('checked')).toHaveLength(1)
  })

  it('not checkable with reason; without write right no button; errors shown', async () => {
    const w = mount(InvoiceCheckPanel, {
      props: { invoice: invoice(2, { check: { status: 'NOT_CHECKABLE', reason: 'noContract' }, canCheck: false }), editable: true },
      global: { plugins: [i18n] },
    })
    expect(w.text()).toContain('Nicht prüfbar')
    expect(w.text()).toContain('Kein Vertrag')
    expect(w.find('.inv-recheck').exists()).toBe(false)

    const post = vi.fn(async () => {
      throw httpError(403)
    })
    Object.assign(useAuthStore().http, { post })
    const u = mount(InvoiceCheckPanel, { props: { invoice: invoice(3, { canCheck: true }), editable: true }, global: { plugins: [i18n] } })
    expect(u.text()).toContain('noch nicht geprüft')
    await u.find('.inv-recheck').trigger('click')
    await flushPromises()
    expect(u.find('[role="alert"]').exists()).toBe(true)
    expect(u.emitted('checked')).toBeUndefined()
  })
})
