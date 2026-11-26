/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Certificate, CertificateList } from '../src/api/certificates'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import CertificatesView from '../src/views/CertificatesView.vue'
import { httpError } from './fixtures/edit'

/* Energy certificates (BA-07) with a mocked API; neutral placeholder data. */

const Stub = defineComponent({ render: () => h('div') })
type Fn = ReturnType<typeof vi.fn>
const mounted: VueWrapper[] = []
const V1 = '2026-11-26T10:00:00.000+01:00'

function cert(id: number, over: Partial<Certificate> = {}, data: Partial<Certificate['data']> = {}): Certificate {
  return {
    id,
    name: 'Verbrauchsausweis 2017',
    site: { id: 1, name: 'Musterwerk', names: {} },
    building: { id: 10, name: 'Musterwerk', names: {} },
    directory: { id: 11, name: 'Energieausweise' },
    data: { type: 'CONSUMPTION', issued: '2017-03-01', validUntil: '2027-02-28', energyClass: 'C', heatConsumption: 112.5, responsible: [50], reminderMonths: [6, 1], ...data },
    validUntil: data.validUntil ?? '2027-02-28',
    status: 'EXPIRING',
    daysLeft: 94,
    responsible: { count: 1, names: ['Energieteam'] },
    file: null,
    lastReminder: null,
    reminderError: null,
    transfers: {},
    canWrite: true,
    canDelete: true,
    version: V1,
    ...over,
  }
}

function list(certificates: Certificate[] = [cert(100), cert(101, { status: 'VALID', validUntil: '2031-05-31', daysLeft: 1647 }, { validUntil: '2031-05-31', type: 'DEMAND', finalEnergy: 95 })]): CertificateList {
  return {
    today: '2026-11-26',
    certificates,
    buildings: [
      { id: 10, name: 'Musterwerk', names: {}, directory: 11, canCreate: true },
      { id: 20, name: 'Außenlager', names: {}, directory: null, canCreate: false },
    ],
    transfers: [
      { id: 60, name: 'Liegenschaftsamt (Beispiel)', site: null, enabled: 'true', channel: 'MAIL', format: 'CSV', schedule: 'MONTHLY', lastTransfer: null, requested: null, canRun: true },
    ],
    people: [{ id: 50, name: 'Energieteam', kind: 'GROUP', email: false }],
  }
}

async function mountView(http: Partial<Record<'get' | 'post' | 'put' | 'delete', Fn>>) {
  const auth = useAuthStore()
  Object.assign(auth.http, http)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'home', component: Stub }] })
  await router.push('/')
  const wrapper = mount(CertificatesView, { global: { plugins: [router, i18n] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

function field(w: VueWrapper, label: string) {
  const l = w.findAll('[role="dialog"] label').find((x) => x.text().replace(/\s*\*$/, '').trim().startsWith(label))
  if (!l) throw new Error('no field ' + label)
  return w.find<HTMLInputElement>('#' + l.attributes('for'))
}

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
})
afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
})

describe('energy certificates (BA-07)', () => {
  it('lists the certificates with status and filters by "Handlungsbedarf"', async () => {
    const w = await mountView({ get: vi.fn(async () => list()) })
    const rows = w.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('Läuft ab')
    expect(rows[0].text()).toContain('noch 94 Tage')
    expect(rows[0].text()).toContain('112,5 kWh/(m²·a)')
    expect(rows[1].text()).toContain('Bedarfsausweis')
    await w.findAll('input[name="ce-status"]').find((i) => (i.element as HTMLInputElement).value === 'attention')!.setValue(true)
    expect(w.findAll('tbody tr')).toHaveLength(1)
  })

  it('creates a certificate: suggests the validity and sends the data', async () => {
    const created = cert(102, { version: V1 }, { issued: '2024-05-02', validUntil: '2034-05-01' })
    const post = vi.fn(async () => created)
    const get = vi.fn(async () => list())
    const w = await mountView({ get, post })
    await w.findAll('button').find((b) => b.text() === 'Neuer Energieausweis')!.trigger('click')
    await flushPromises()
    expect(w.find('[role="dialog"] h2').text()).toBe('Neuer Energieausweis')
    await field(w, 'Ausgestellt am').setValue('2024-05-02')
    await field(w, 'Ausgestellt am').trigger('change')
    expect(field(w, 'Gültig bis').element.value).toBe('2034-05-01')
    await field(w, 'Effizienzklasse').setValue('B')
    await field(w, 'Energieverbrauchskennwert Wärme').setValue('98,4')
    await w.find('[role="dialog"] form').trigger('submit')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/certificates', {
      building: 10,
      data: expect.objectContaining({ type: 'CONSUMPTION', issued: '2024-05-02', validUntil: '2034-05-01', energyClass: 'B', heatConsumption: 98.4, reminderMonths: [6, 1] }),
    })
    expect(w.text()).toContain('wurde angelegt')
  })

  it('shows field problems from the form and from the server', async () => {
    const put = vi.fn(async () => {
      throw httpError(400, 'responsible:person')
    })
    const w = await mountView({ get: vi.fn(async () => list()), put })
    await w.find('tbody .btn-link').trigger('click')
    await flushPromises()
    await field(w, 'Gültig bis').setValue('2016-01-01')
    await w.find('[role="dialog"] form').trigger('submit')
    await flushPromises()
    expect(put).not.toHaveBeenCalled()
    expect(w.text()).toContain('„Gültig bis“ muss nach dem Ausstellungsdatum liegen.')

    await field(w, 'Gültig bis').setValue('2027-03-31')
    await w.find('[role="dialog"] form').trigger('submit')
    await flushPromises()
    expect(put).toHaveBeenCalledWith('/web/certificates/100', expect.objectContaining({ version: V1 }))
    expect(w.text()).toContain('Bitte nur Benutzer oder Gruppen wählen.')
  })

  it('requests a transfer', async () => {
    const post = vi.fn(async () => ({ ...list().transfers[0], requested: { at: '2026-11-26T10:05:00', by: 'demo' } }))
    const w = await mountView({ get: vi.fn(async () => list()), post })
    await w.findAll('[role="tab"]')[1].trigger('click')
    expect(w.text()).toContain('Monatlich')
    await w.findAll('button').find((b) => b.text() === 'Jetzt übermitteln')!.trigger('click')
    await flushPromises()
    expect(post).toHaveBeenCalledWith('/web/certificates/transfers/60/run')
    expect(w.text()).toContain('wurde angefordert')
    expect(w.text()).toContain('von demo')
  })
})
