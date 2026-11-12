<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { hasModules } from '../../api/navigation'
import { catalog, entryRoute, favouriteEntries } from '../../nav/catalog'
import { menuGroups, openGroupOfRoute, singleModules, type GroupId, type MenuModule } from '../../nav/menu'
import { FAVOURITES_IN_MENU } from '../../nav/prefs'
import { useNavigationStore } from '../../stores/navigation'
import { useNavPrefsStore } from '../../stores/navPrefs'
import NavIcon from './NavIcon.vue'

/*
 * Sidebar: Start, Dashboards and Analysen (overview pages, below them the user's favourites), the module groups
 * (collapsible, state remembered; the group of the current route opens) and Berichte. As a rail (`rail`) only the
 * icons are shown; their names stay the accessible names and appear as tooltips.
 */

const props = defineProps<{ rail: boolean }>()
const emit = defineEmits<{ navigate: []; expand: [] }>()
const { t, locale } = useI18n()
const nav = useNavigationStore()
const prefs = useNavPrefsStore()
const route = useRoute()

/* the analyses page offers "Neue Analyse" when a directory allows it: then the entry is shown without analyses too */
onMounted(() => void nav.loadDirectories())

const groups = computed(() => menuGroups(nav.modules))
const singles = computed(() => singleModules(nav.modules))

const sections = computed(() => {
  const list: { kind: 'dashboard' | 'analysis'; route: string; title: string; favourites: ReturnType<typeof catalog> }[] = []
  if (nav.counts.dashboards > 0) {
    const all = catalog(nav.data, 'dashboard', locale.value)
    list.push({ kind: 'dashboard', route: 'dashboards', title: t('nav.dashboards'), favourites: favouriteEntries(all, prefs.favourites, FAVOURITES_IN_MENU) })
  }
  if (nav.counts.analyses > 0 || nav.creatableDirectories.length) {
    const all = catalog(nav.data, 'analysis', locale.value)
    list.push({ kind: 'analysis', route: 'analyses', title: t('nav.analyses'), favourites: favouriteEntries(all, prefs.favourites, FAVOURITES_IN_MENU) })
  }
  return list
})

const startRoute = computed(() => {
  const id = nav.data?.startDashboard
  return typeof id === 'number' && id > 0 ? { name: 'dashboard', params: { id } } : { name: 'home' }
})

const empty = computed(() => !sections.value.length && !hasModules(nav.modules))

/* the group of the current module opens (and stays open until closed) */
watch(
  () => route.name,
  (name) => prefs.setGroups(openGroupOfRoute(prefs.groups, name)),
  { immediate: true },
)

const isOpen = (id: GroupId) => !props.rail && prefs.groups[id] === true

function toggle(id: GroupId) {
  if (props.rail) {
    /* the rail has no room for the modules: show the full sidebar with the group open */
    prefs.setGroupOpen(id, true)
    emit('expand')
    return
  }
  prefs.setGroupOpen(id, !isOpen(id))
}

const label = (m: MenuModule) => t('modules.' + m.key)
const tip = (text: string) => (props.rail ? text : undefined)
</script>

<template>
  <div class="side-nav" :class="{ rail }">
    <ul class="nav-list">
      <li>
        <RouterLink :to="startRoute" class="nav-link" :title="tip(t('nav.start'))" @click="emit('navigate')">
          <NavIcon name="home" /><span class="label">{{ t('nav.start') }}</span>
        </RouterLink>
      </li>
      <li v-for="s in sections" :key="s.kind" class="nav-section">
        <RouterLink :to="{ name: s.route }" class="nav-link" :title="tip(s.title)" @click="emit('navigate')">
          <NavIcon :name="s.kind" /><span class="label">{{ s.title }}</span>
        </RouterLink>
        <ul v-if="!rail && s.favourites.length" class="fav-list" :aria-label="t('nav.favouritesOf', { title: s.title })">
          <li v-for="e in s.favourites" :key="e.key">
            <RouterLink :to="entryRoute(e)" class="fav-link" :title="e.sitePath ?? undefined" @click="emit('navigate')">
              <span class="fav-star" aria-hidden="true">★</span><span class="label">{{ e.name }}</span>
            </RouterLink>
          </li>
        </ul>
      </li>
      <li v-for="g in groups" :key="g.id" class="nav-group">
        <button
          type="button"
          class="nav-link group-btn"
          :aria-expanded="isOpen(g.id)"
          :aria-controls="rail ? undefined : 'nav-group-' + g.id"
          :title="tip(t('nav.groups.' + g.id))"
          @click="toggle(g.id)"
        >
          <NavIcon :name="g.id" />
          <span class="label">{{ t('nav.groups.' + g.id) }}</span>
          <template v-if="!isOpen(g.id)">
            <span v-if="g.open > 0" class="module-count is-alert" :class="{ 'rail-badge': rail }">
              <span aria-hidden="true">{{ g.open }}</span>
              <span class="visually-hidden">{{ t('modules.alarmsOpen', g.open) }}</span>
            </span>
            <span v-else-if="!rail" class="group-count">
              <span aria-hidden="true">({{ g.modules.length }})</span>
              <span class="visually-hidden">{{ t('nav.groupModules', g.modules.length) }}</span>
            </span>
          </template>
          <span v-if="!rail" class="chevron" :class="{ open: isOpen(g.id) }"><NavIcon name="chevron" /></span>
        </button>
        <ul v-show="isOpen(g.id)" :id="'nav-group-' + g.id" class="group-list">
          <li v-for="m in g.modules" :key="m.key">
            <RouterLink :to="{ name: m.route }" class="module-item" @click="emit('navigate')">
              <span class="label">{{ label(m) }}</span>
              <span v-if="m.key === 'alarms'" class="module-count" :class="{ 'is-alert': m.open > 0 }" :title="t('modules.alarmsOpen', m.open)">
                <span aria-hidden="true">{{ m.open }}</span>
                <span class="visually-hidden">{{ t('modules.alarmsOpen', m.open) }}</span>
              </span>
              <span v-else class="module-count">{{ m.count }}</span>
            </RouterLink>
          </li>
        </ul>
      </li>
      <li v-for="m in singles" :key="m.key">
        <RouterLink :to="{ name: m.route }" class="nav-link" :title="tip(label(m))" @click="emit('navigate')">
          <NavIcon name="reports" /><span class="label">{{ label(m) }}</span>
          <span v-if="!rail" class="module-count">{{ m.count }}</span>
        </RouterLink>
      </li>
    </ul>
    <p v-if="empty && !rail" class="side-info">{{ t('nav.empty') }}</p>
    <button
      type="button"
      class="nav-link collapse-btn"
      :aria-label="rail ? t('nav.expandLabel') : t('nav.collapseLabel')"
      :title="rail ? t('nav.expandLabel') : undefined"
      @click="prefs.setCollapsed(!rail)"
    >
      <NavIcon :name="rail ? 'expand' : 'collapse'" /><span class="label" aria-hidden="true">{{ t('nav.collapse') }}</span>
    </button>
  </div>
</template>

<style scoped>
.side-nav {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.nav-list,
.fav-list,
.group-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.nav-list > li + li {
  margin-top: 2px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.5rem 0.55rem;
  font: inherit;
  font-weight: 600;
  color: inherit;
  text-align: left;
  text-decoration: none;
  background: none;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.nav-link .label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-link:hover,
.fav-link:hover,
.module-item:hover {
  background: rgba(255, 255, 255, 0.08);
}

.nav-link.router-link-exact-active,
.fav-link.router-link-active,
.module-item.router-link-active {
  background: var(--sun);
  color: var(--blueberry);
}

.fav-list {
  margin: 1px 0 4px;
}

.fav-link {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.3rem 0.55rem 0.3rem 2.2rem;
  color: inherit;
  text-decoration: none;
  border-radius: 4px;
  font-size: 0.93rem;
}

.fav-link .label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fav-star {
  color: var(--sun);
  font-size: 0.85rem;
}

.fav-link.router-link-active .fav-star {
  color: var(--blueberry);
}

.group-count {
  font-weight: 400;
  font-size: 0.85rem;
  opacity: 0.75;
}

.chevron {
  display: inline-flex;
  opacity: 0.7;
  transition: transform 0.15s;
}

.chevron :deep(svg) {
  width: 16px;
  height: 16px;
}

.chevron.open {
  transform: rotate(90deg);
}

.group-list {
  margin: 1px 0 4px;
}

.module-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  padding: 0.35rem 0.55rem 0.35rem 2.2rem;
  color: inherit;
  text-decoration: none;
  border-radius: 4px;
}

.module-count {
  min-width: 1.6rem;
  padding: 0 0.4rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.15);
  font-size: 0.8rem;
  font-weight: 600;
  text-align: center;
}

.router-link-active .module-count,
.router-link-exact-active .module-count {
  background: var(--blueberry);
  color: var(--sun);
}

/* open alarms: warning colour (the active entry is yellow itself, there the badge turns red) */
.module-count.is-alert {
  background: var(--sun);
  color: var(--blueberry);
  font-weight: 700;
}

.router-link-active .module-count.is-alert {
  background: var(--error);
  color: #fff;
}

.side-info {
  opacity: 0.8;
  padding: 0.5rem;
}

.collapse-btn {
  margin-top: auto;
  font-weight: 400;
  opacity: 0.8;
}

.collapse-btn:hover {
  opacity: 1;
}

.nav-link:focus-visible,
.fav-link:focus-visible,
.module-item:focus-visible {
  outline: 3px solid var(--sun);
  outline-offset: -3px;
}

/* rail: icons only, names for screen readers and as tooltips */
.rail .nav-link {
  justify-content: center;
  position: relative;
  padding: 0.6rem 0;
}

.rail .label {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.rail-badge {
  position: absolute;
  top: 2px;
  right: 4px;
  min-width: 1.1rem;
  padding: 0 0.25rem;
  font-size: 0.7rem;
}

@media (max-width: 800px) {
  .collapse-btn {
    display: none;
  }
}
</style>
