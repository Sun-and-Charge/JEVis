/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { flushPromises, mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, type Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { i18n } from '../src/i18n'
import { useAuthStore } from '../src/stores/auth'
import ActionPlansView from '../src/views/ActionPlansView.vue'
import LegalView from '../src/views/LegalView.vue'
import NonconformitiesView from '../src/views/NonconformitiesView.vue'
import { httpError } from './fixtures/edit'
import { action, actionPlan, legalPlan, nonconformity, nonconformityPlan, obligation } from './fixtures/iso'

/* Editing of the ISO modules (E2) with a mocked API; neutral data ("Musterwerk"). */

const Stub = defineComponent({ render: () => h('div') })
type Fn = ReturnType<typeof vi.fn>
const mounted: VueWrapper[] = []
const V = '2026-11-01T10:00:00+01:00'
const rights = { version: V, canWrite: true, canDelete: true, canAttach: true }

async function mountWith(component: Component, http: Partial<Record<'get' | 'post' | 'put' | 'delete' | 'postForm', Fn>>) {
  const auth = useAuthStore()
  Object.assign(auth.http, http)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'home', component: Stub }] })
  await router.push('/')
  const wrapper = mount(component, { global: { plugins: [router, i18n] }, attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

const dialog = (w: VueWrapper) => w.find('[role="dialog"]')

/** The input / select / textarea of a labelled field in the dialog. */
function control(w: VueWrapper, label: string): DOMWrapper<HTMLInputElement> {
  const l = dialog(w)
    .findAll('label')
    .find((x) => x.text().replace(/\s*\*$/, '').trim() === label)
  if (!l) throw new Error('no field ' + label)
  return dialog(w).find<HTMLInputElement>('#' + l.attributes('for'))
}

async function openRow(w: VueWrapper, index = 0) {
  await w.findAll('tbody tr.mod-row')[index].find('button.mod-toggle').trigger('click')
}

async function submit(w: VueWrapper) {
  await dialog(w).find('form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  setActivePinia(createPinia())
  i18n.global.locale.value = 'de'
  window.localStorage.clear()
})
afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
})

describe('action plans: editing (E2)', () => {
  it('without rights everything stays read-only', async () => {
    const get = vi.fn(async () => ({ today: '2026-11-06', plans: [actionPlan(1, [action(1, { attachments: 0 })], { name: 'Musterwerk' })] }))
    const w = await mountWith(ActionPlansView, { get })
    expect(w.find('.read-only').exists()).toBe(true)
    expect(w.find('.iso-new').exists()).toBe(false)
    await openRow(w)
    expect(w.find('.iso-edit').exists()).toBe(false)
    expect(w.find('.iso-delete').exists()).toBe(false)
    expect(w.find('.iso-upload').exists()).toBe(false)
  })

  it('creates an action: done status sets today, only filled fields are sent, the new item opens', async () => {
    const plan = actionPlan(1, [action(1, rights)], { name: 'Musterwerk', canCreate: true })
    const get = vi.fn(async () => ({ today: '2026-11-06', plans: [plan] }))
    const created = action(2, { ...rights, number: 'AP 002', title: 'Musterwerk Beleuchtung', status: 'Done', doneDate: '2026-11-06T00:00:00.000+01:00' })
    const post = vi.fn(async () => created)
    const w = await mountWith(ActionPlansView, { get, post })
    expect(w.find('.read-only').exists()).toBe(false)

    await w.find('.iso-new').trigger('click')
    expect(dialog(w).find('h2').text()).toBe('Neue Maßnahme')
    expect(dialog(w).text()).toContain('werden im Control Center berechnet')

    // required title
    await submit(w)
    expect(post).not.toHaveBeenCalled()
    const title = control(w, 'Titel')
    expect(title.attributes('aria-invalid')).toBe('true')
    expect(document.getElementById(title.attributes('aria-describedby')!)?.textContent).toContain('Bitte ausfüllen')

    await title.setValue('Musterwerk Beleuchtung')
    await control(w, 'Status').setValue('Done')
    expect((control(w, 'Abgeschlossen').element as HTMLInputElement).value).toBe('2026-11-06')
    expect(dialog(w).text()).toContain('Abschlussdatum auf heute gesetzt')
    const chip = dialog(w).findAll('.edit-chip').find((c) => c.text() === 'Area 2')!
    await chip.trigger('click')
    expect(chip.attributes('aria-pressed')).toBe('true')
    await submit(w)

    expect(post).toHaveBeenCalledWith('/web/actions/plans/1/items', {
      Title: 'Musterwerk Beleuchtung',
      'Status Tags': 'Done',
      'Done Date': '2026-11-06',
      'Field Tags': ['Area 2'],
    })
    expect(dialog(w).exists()).toBe(false)
    expect(w.findAll('tbody tr.mod-row')).toHaveLength(2)
    expect(w.find('#actions-d-2').exists()).toBe(true)
    expect(w.find('.iso-result').text()).toContain('AP 002 – Musterwerk Beleuchtung')
  })

  it('edits only the changed fields with the version; 409 takes over the current item', async () => {
    const item = action(1, { ...rights, title: 'Musterwerk Pumpe', status: 'Alt' })
    const get = vi.fn(async () => ({ today: '2026-11-06', plans: [actionPlan(1, [item], { name: 'Musterwerk' })] }))
    const current = { ...item, title: 'Musterwerk Pumpe (geändert)', version: '2026-11-02T08:00:00+01:00' }
    const put = vi
      .fn()
      .mockRejectedValueOnce(httpError(409, current))
      .mockImplementationOnce(async (_p: string, body: { fields: Record<string, unknown> }) => ({ ...current, description: body.fields.Description, version: '2026-11-03T08:00:00+01:00' }))
    const w = await mountWith(ActionPlansView, { get, put })
    await openRow(w)
    expect(w.find('.iso-calc-note').text()).toContain('Control Center')
    await w.find('.iso-edit').trigger('click')

    // a status no longer in the list is shown and kept
    const status = control(w, 'Status')
    expect(status.element.value).toBe('Alt')
    expect(status.findAll('option').map((o) => o.text())).toContain('Alt (nicht mehr in der Liste)')

    await control(w, 'Beschreibung').setValue('Neue Pumpe')
    await submit(w)
    expect(put).toHaveBeenLastCalledWith('/web/actions/items/1', { version: V, fields: { Description: 'Neue Pumpe' } })
    // 409: message, form shows the current item, the list too
    expect(dialog(w).find('[role="alert"]').text()).toContain('inzwischen geändert')
    expect(control(w, 'Titel').element.value).toBe('Musterwerk Pumpe (geändert)')
    expect(control(w, 'Beschreibung').element.value).toBe('')
    expect(w.find('table.iso-table tbody').text()).toContain('Musterwerk Pumpe (geändert)')

    await control(w, 'Beschreibung').setValue('Neue Pumpe')
    await submit(w)
    expect(put).toHaveBeenLastCalledWith('/web/actions/items/1', { version: '2026-11-02T08:00:00+01:00', fields: { Description: 'Neue Pumpe' } })
    expect(dialog(w).exists()).toBe(false)
    expect(w.find('.iso-result').text()).toContain('gespeichert')
  })

  it('deletes after the danger question; the item leaves the list', async () => {
    const get = vi.fn(async () => ({ today: '2026-11-06', plans: [actionPlan(1, [action(1, rights), action(2, rights)])] }))
    const del = vi.fn(async () => undefined)
    const w = await mountWith(ActionPlansView, { get, delete: del })
    await openRow(w)
    await w.find('.iso-delete').trigger('click')
    expect(dialog(w).text()).toContain('als gelöscht markiert')
    expect(dialog(w).find('.edit-submit').classes()).toContain('is-danger')
    await dialog(w).find('.edit-submit').trigger('click')
    await flushPromises()
    expect(del).toHaveBeenCalledWith(`/web/actions/items/1?version=${encodeURIComponent(V)}`)
    expect(w.findAll('tbody tr.mod-row')).toHaveLength(1)
    expect(w.find('.iso-result').text()).toContain('gelöscht')
  })

  it('a refused delete (403) stays in the question with the reason', async () => {
    const get = vi.fn(async () => ({ today: '2026-11-06', plans: [actionPlan(1, [action(1, rights)])] }))
    const del = vi.fn().mockRejectedValue(httpError(403, 'no delete right'))
    const w = await mountWith(ActionPlansView, { get, delete: del })
    await openRow(w)
    await w.find('.iso-delete').trigger('click')
    await dialog(w).find('.edit-submit').trigger('click')
    await flushPromises()
    expect(dialog(w).find('[role="alert"]').text()).toContain('no delete right')
    expect(w.findAll('tbody tr.mod-row')).toHaveLength(1)
  })

  it('uploads and deletes attachments; files over 25 MB are refused here', async () => {
    const get = vi.fn(async (path: string) => {
      if (path.startsWith('/web/actionplans')) return { today: '2026-11-06', plans: [actionPlan(1, [action(1, { ...rights, attachments: 1 })])] }
      if (path === '/web/objects/1/attachments') return [{ id: 77, name: 'angebot.pdf', size: 2048, date: '2026-10-01T10:00:00' }]
      throw new Error(path)
    })
    const postForm = vi.fn(async () => ({ id: 78, name: 'foto.jpg', size: 3 }))
    const del = vi.fn(async () => undefined)
    const w = await mountWith(ActionPlansView, { get, postForm, delete: del })
    await openRow(w)
    await flushPromises()
    expect(w.findAll('.iso-files li')).toHaveLength(1)

    const input = w.find<HTMLInputElement>('.iso-upload input[type="file"]')
    const big = new File(['x'], 'gross.zip')
    Object.defineProperty(big, 'size', { value: 26 * 1024 * 1024 })
    Object.defineProperty(input.element, 'files', { value: [big], configurable: true })
    await input.trigger('change')
    await flushPromises()
    expect(postForm).not.toHaveBeenCalled()
    expect(w.find('.iso-attach-message').text()).toContain('größer als 25 MB')

    Object.defineProperty(input.element, 'files', { value: [new File(['abc'], 'foto.jpg')], configurable: true })
    await input.trigger('change')
    await flushPromises()
    expect(postForm).toHaveBeenCalledTimes(1)
    expect((postForm.mock.calls[0] as unknown[])[0]).toBe('/web/items/1/attachments')
    expect(w.findAll('.iso-files li').map((li) => li.find('.iso-file').text())).toEqual(['angebot.pdf', 'foto.jpg'])
    // the count of the list follows
    const attCol = w.findAll('table.iso-table thead th').findIndex((th) => th.text().startsWith('Anhänge'))
    if (attCol >= 0) expect(w.findAll('tbody tr.mod-row')[0].findAll('td')[attCol].text()).toBe('2')

    // drag & drop
    await w.find('.iso-upload').trigger('drop', { dataTransfer: { files: [new File(['d'], 'plan.dwg')] } })
    await flushPromises()
    expect(postForm).toHaveBeenCalledTimes(2)

    await w.findAll('.iso-file-delete')[0].trigger('click')
    expect(dialog(w).text()).toContain('Papierkorb')
    await dialog(w).find('.edit-submit').trigger('click')
    await flushPromises()
    expect(del).toHaveBeenCalledWith('/web/attachments/77')
    expect(w.findAll('.iso-files li').map((li) => li.find('.iso-file').text())).not.toContain('angebot.pdf')
  })

  it('a per-file right from the server hides the delete button', async () => {
    const get = vi.fn(async (path: string) => {
      if (path.startsWith('/web/actionplans')) return { today: '2026-11-06', plans: [actionPlan(1, [action(1, { ...rights, attachments: 2 })])] }
      return [
        { id: 77, name: 'a.pdf', size: 1, date: null, canDelete: false },
        { id: 78, name: 'b.pdf', size: 1, date: null, canDelete: true },
      ]
    })
    const w = await mountWith(ActionPlansView, { get })
    await openRow(w)
    await flushPromises()
    expect(w.findAll('.iso-file-delete')).toHaveLength(1)
  })
})

describe('nonconformities: editing (E2)', () => {
  it('lists the requirement messages of a 400 next to the form', async () => {
    const plan = nonconformityPlan(1, [nonconformity(1, rights)], { name: 'Musterwerk', canCreate: true })
    const get = vi.fn(async () => ({ plans: [plan] }))
    const post = vi.fn().mockRejectedValue(httpError(400, 'Sofortmaßnahmen erforderlich, aber nicht eingetragen\nAbschlussdatum liegt in der Zukunft'))
    const w = await mountWith(NonconformitiesView, { get, post })
    await w.find('.iso-new').trigger('click')
    expect(dialog(w).find('h2').text()).toBe('Neue Abweichung')
    await control(w, 'Titel').setValue('Musterwerk Leck')
    await control(w, 'Medium').setValue('Gas')
    const check = dialog(w)
      .findAll('label.iso-edit-check')
      .find((l) => l.text().includes('Müssen Sofortmaßnahmen ergriffen werden?'))!
    await check.find('input').setValue(true)
    await submit(w)
    expect(post).toHaveBeenCalledWith('/web/nonconformities/plans/1/items', {
      Title: 'Musterwerk Leck',
      Medium: 'Gas',
      'Check List': expect.objectContaining({ 'Immediate Action Required': true, Design: false }),
    })
    const box = dialog(w).find('.iso-messages')
    expect(box.attributes('role')).toBe('alert')
    expect(box.text()).toContain('erfüllt die Anforderungen nicht')
    expect(box.findAll('li').map((li) => li.text())).toEqual(['Sofortmaßnahmen erforderlich, aber nicht eingetragen', 'Abschlussdatum liegt in der Zukunft'])
    expect(dialog(w).find('form').attributes('aria-describedby')).toBe(box.attributes('id'))
  })

  it('edits and deletes with the item rights; 404 reloads', async () => {
    const get = vi.fn(async () => ({ plans: [nonconformityPlan(1, [nonconformity(1, { ...rights, canDelete: false }), nonconformity(2)])] }))
    const put = vi.fn().mockRejectedValue(httpError(404))
    const w = await mountWith(NonconformitiesView, { get, put })
    await openRow(w, 1)
    expect(w.findAll('.iso-edit')).toHaveLength(0)
    await openRow(w, 0)
    expect(w.findAll('.iso-edit')).toHaveLength(1)
    expect(w.find('.iso-delete').exists()).toBe(false)
    await w.find('.iso-edit').trigger('click')
    await control(w, 'Ursache').setValue('Dichtung')
    await submit(w)
    expect(put).toHaveBeenCalledWith('/web/nonconformities/items/1', { version: V, fields: { Cause: 'Dichtung' } })
    expect(dialog(w).find('[role="alert"]').text()).toContain('nicht mehr vorhanden')
    expect(get).toHaveBeenCalledTimes(2)
  })
})

describe('legal register: editing (E2)', () => {
  it('creates with category, scope and relevance; edits keep an old category', async () => {
    const item = obligation(1, { ...rights, title: 'Musterwerk Verordnung', category: 'Alte Kategorie', relevant: null })
    const plan = legalPlan(1, [item], { name: 'Musterwerk', canCreate: true })
    const get = vi.fn(async () => ({ plans: [plan] }))
    const post = vi.fn(async () => obligation(2, { ...rights, title: 'Neue Vorschrift' }))
    const put = vi.fn(async () => ({ ...item, designation: 'BImSchV' }))
    const w = await mountWith(LegalView, { get, post, put })

    await w.find('.iso-new').trigger('click')
    expect(dialog(w).find('h2').text()).toBe('Neue Rechtsvorschrift')
    await control(w, 'Titel').setValue('Neue Vorschrift')
    await control(w, 'Kategorie').setValue('Category 2')
    await control(w, 'Relevant für ISO 50001').setValue(true)
    await control(w, 'Prüfdatum').setValue('2027-03-01')
    await control(w, 'Link').setValue('nur Text')
    await submit(w)
    expect(post).not.toHaveBeenCalled()
    expect(dialog(w).text()).toContain('http:// oder https://')
    await control(w, 'Link').setValue('https://example.org/vo')
    await submit(w)
    expect(post).toHaveBeenCalledWith('/web/obligations/plans/1/items', {
      title: 'Neue Vorschrift',
      category: 'Category 2',
      'Relevance 50001': true,
      link: 'https://example.org/vo',
      'Date of Examination': '2027-03-01',
    })
    expect(w.findAll('tbody tr.mod-row')).toHaveLength(2)

    // edit the old one: its category is no longer in the list but kept
    await openRow(w, 0)
    await w.findAll('.iso-edit')[0].trigger('click')
    expect(control(w, 'Kategorie').element.value).toBe('Alte Kategorie')
    await control(w, 'Bezeichnung').setValue('BImSchV')
    await submit(w)
    expect(put).toHaveBeenCalledWith('/web/obligations/items/1', { version: V, fields: { Designation: 'BImSchV' } })
  })

  it('nothing changed: no request', async () => {
    const get = vi.fn(async () => ({ plans: [legalPlan(1, [obligation(1, rights)])] }))
    const put = vi.fn()
    const w = await mountWith(LegalView, { get, put })
    await openRow(w)
    await w.find('.iso-edit').trigger('click')
    await submit(w)
    expect(put).not.toHaveBeenCalled()
    expect(dialog(w).text()).toContain('Keine Änderungen')
  })
})
