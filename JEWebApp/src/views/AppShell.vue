<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { hasModules, localName, startDashboardFor } from '../api/navigation'
import LanguageSwitch from '../components/LanguageSwitch.vue'
import MaintenanceBanner from '../components/MaintenanceBanner.vue'
import NavTree from '../components/NavTree.vue'
import NewAnalysisButton from '../components/analysis/edit/NewAnalysisButton.vue'
import { useAuthStore } from '../stores/auth'
import { useNavigationStore } from '../stores/navigation'

const { t, locale } = useI18n()
const auth = useAuthStore()
const nav = useNavigationStore()
const router = useRouter()
const route = useRoute()
const menuOpen = ref(false)
const userMenuOpen = ref(false)

/* after login (the shell is mounted once per session): the user's start dashboard instead of the home page */
onMounted(async () => {
  if (nav.data) return
  await nav.load()
  const start = startDashboardFor(nav.data, route.name)
  if (start != null) router.replace({ name: 'dashboard', params: { id: start } })
})

/* session ended (logout or expired token) while the app is open */
watch(
  () => auth.isLoggedIn,
  (loggedIn) => {
    if (!loggedIn) {
      nav.reset()
      router.replace({ name: 'login', query: auth.sessionExpired ? { redirect: route.fullPath } : {} })
    }
  },
)

async function logout() {
  userMenuOpen.value = false
  await auth.logout()
}
</script>

<template>
  <div class="shell" :class="{ 'menu-open': menuOpen }">
    <a href="#main" class="visually-hidden">{{ t('app.skip') }}</a>
    <header class="top">
      <button type="button" class="menu-btn" :aria-label="t('header.menu')" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">☰</button>
      <RouterLink :to="{ name: 'home' }" class="brand">
        <img src="/suc_logo_dark.png" alt="Sun and Charge" />
        <span>{{ t('app.name') }}</span>
      </RouterLink>
      <div class="spacer" />
      <LanguageSwitch />
      <div class="user">
        <button type="button" class="user-btn" :aria-expanded="userMenuOpen" aria-haspopup="menu" @click="userMenuOpen = !userMenuOpen">
          {{ auth.user?.name }} ▾
        </button>
        <ul v-if="userMenuOpen" class="user-menu" role="menu" @click="userMenuOpen = false">
          <li role="none">
            <RouterLink role="menuitem" :to="{ name: 'password' }">{{ t('header.changePassword') }}</RouterLink>
          </li>
          <li role="none">
            <button role="menuitem" type="button" class="btn-link" @click="logout">{{ t('header.logout') }}</button>
          </li>
        </ul>
      </div>
    </header>

    <nav class="side" :aria-label="t('nav.sites')">
      <p v-if="nav.loading" class="side-info">{{ t('nav.loading') }}</p>
      <p v-else-if="nav.failed" class="side-info">{{ t('nav.error') }}</p>
      <template v-else-if="nav.data">
        <NewAnalysisButton @navigate="menuOpen = false" />
        <ul v-if="hasModules(nav.modules)" class="modules" :aria-label="t('modules.title')">
          <li v-if="nav.modules.appointments">
            <RouterLink :to="{ name: 'appointments' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.appointments') }}</span><span class="module-count">{{ nav.modules.appointments }}</span>
            </RouterLink>
          </li>
          <li v-if="nav.modules.maintenance">
            <RouterLink :to="{ name: 'maintenance' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.maintenance') }}</span><span class="module-count">{{ nav.modules.maintenance }}</span>
            </RouterLink>
          </li>
          <li v-if="nav.modules.actionPlans">
            <RouterLink :to="{ name: 'actionPlans' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.actionPlans') }}</span><span class="module-count">{{ nav.modules.actionPlans }}</span>
            </RouterLink>
          </li>
          <li v-if="nav.modules.nonconformities">
            <RouterLink :to="{ name: 'nonconformities' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.nonconformities') }}</span><span class="module-count">{{ nav.modules.nonconformities }}</span>
            </RouterLink>
          </li>
          <li v-if="nav.modules.legal">
            <RouterLink :to="{ name: 'legal' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.legal') }}</span><span class="module-count">{{ nav.modules.legal }}</span>
            </RouterLink>
          </li>
          <li v-if="nav.modules.meters">
            <RouterLink :to="{ name: 'meters' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.meters') }}</span><span class="module-count">{{ nav.modules.meters }}</span>
            </RouterLink>
          </li>
          <li v-if="nav.modules.basedata">
            <RouterLink :to="{ name: 'basedata' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.basedata') }}</span><span class="module-count">{{ nav.modules.basedata }}</span>
            </RouterLink>
          </li>
          <li v-if="nav.modules.equipment">
            <RouterLink :to="{ name: 'equipment' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.equipment') }}</span><span class="module-count">{{ nav.modules.equipment }}</span>
            </RouterLink>
          </li>
          <li v-if="nav.modules.notes">
            <RouterLink :to="{ name: 'notes' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.notes') }}</span><span class="module-count">{{ nav.modules.notes }}</span>
            </RouterLink>
          </li>
          <li v-if="nav.modules.alarms">
            <RouterLink :to="{ name: 'alarms' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.alarms') }}</span>
              <span class="module-count" :class="{ 'is-alert': nav.modules.alarmsOpen > 0 }" :title="t('modules.alarmsOpen', nav.modules.alarmsOpen)">
                <span aria-hidden="true">{{ nav.modules.alarmsOpen }}</span>
                <span class="visually-hidden">{{ t('modules.alarmsOpen', nav.modules.alarmsOpen) }}</span>
              </span>
            </RouterLink>
          </li>
          <li v-if="nav.modules.reports">
            <RouterLink :to="{ name: 'reports' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.reports') }}</span><span class="module-count">{{ nav.modules.reports }}</span>
            </RouterLink>
          </li>
          <li v-if="nav.modules.contracts">
            <RouterLink :to="{ name: 'contracts' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.contracts') }}</span><span class="module-count">{{ nav.modules.contracts }}</span>
            </RouterLink>
          </li>
          <li v-if="nav.modules.invoices">
            <RouterLink :to="{ name: 'invoices' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.invoices') }}</span><span class="module-count">{{ nav.modules.invoices }}</span>
            </RouterLink>
          </li>
          <li v-if="nav.modules.costs">
            <RouterLink :to="{ name: 'costs' }" class="other-item module-item" @click="menuOpen = false">
              <span>{{ t('modules.costs') }}</span><span class="module-count">{{ nav.modules.costs }}</span>
            </RouterLink>
          </li>
        </ul>
        <ul class="tree">
          <NavTree v-for="node in nav.tree" :key="node.site.id" :node="node" @navigate="menuOpen = false" />
        </ul>
        <div v-if="nav.data.other.analyses.length || nav.data.other.dashboards.length" class="other">
          <p class="other-title">{{ t('nav.other') }}</p>
          <RouterLink
            v-for="d in nav.data.other.dashboards"
            :key="'d' + d.id"
            :to="{ name: 'dashboard', params: { id: d.id } }"
            class="other-item"
            @click="menuOpen = false"
          >
            {{ localName(d, locale) }}
          </RouterLink>
          <RouterLink
            v-for="a in nav.data.other.analyses"
            :key="'a' + a.id"
            :to="{ name: 'analysis', params: { id: a.id } }"
            class="other-item"
            @click="menuOpen = false"
          >
            {{ localName(a, locale) }}
          </RouterLink>
        </div>
        <p v-if="!nav.counts.analyses && !nav.counts.dashboards && !hasModules(nav.modules)" class="side-info">{{ t('nav.empty') }}</p>
      </template>
    </nav>

    <main id="main" class="content" tabindex="-1">
      <MaintenanceBanner v-if="nav.data" />
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: var(--sidebar-w) 1fr;
  grid-template-rows: var(--header-h) 1fr;
  height: 100%;
}

.top {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0 1rem;
  background: var(--blueberry);
  color: #fff;
  border-bottom: 3px solid var(--sun);
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  color: #fff;
  text-decoration: none;
  font-weight: 600;
  font-size: 1.1rem;
}

.brand img {
  height: 32px;
}

.spacer {
  flex: 1;
}

.menu-btn {
  display: none;
  font-size: 1.3rem;
  background: none;
  border: none;
  color: #fff;
  cursor: pointer;
}

.user {
  position: relative;
}

.user-btn {
  font: inherit;
  color: #fff;
  background: none;
  border: none;
  cursor: pointer;
}

.user-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  min-width: 200px;
  margin: 0;
  padding: 0.4rem 0;
  list-style: none;
  background: var(--surface);
  color: var(--ink);
  border-radius: var(--radius);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.18);
  z-index: 10;
}

.user-menu a,
.user-menu .btn-link {
  display: block;
  width: 100%;
  padding: 0.5rem 1rem;
  text-align: left;
  text-decoration: none;
  color: inherit;
}

.user-menu a:hover,
.user-menu .btn-link:hover {
  background: var(--bg);
}

.side {
  background: var(--blueberry-light);
  color: #fff;
  overflow-y: auto;
  padding: 0.75rem 0.5rem;
}

.tree {
  margin: 0;
  padding: 0;
}

.side-info {
  opacity: 0.8;
  padding: 0.5rem;
}

.other {
  margin-top: 0.75rem;
  border-top: 1px solid rgba(255, 255, 255, 0.15);
  padding-top: 0.5rem;
}

.other-title {
  margin: 0;
  padding: 0.3rem 0.5rem;
  font-weight: 600;
}

.other-item {
  display: block;
  padding: 0.35rem 1.5rem;
  color: inherit;
  text-decoration: none;
  border-radius: 4px;
}

.other-item.router-link-active {
  background: var(--sun);
  color: var(--blueberry);
}

.modules {
  list-style: none;
  margin: 0 0 0.75rem;
  padding: 0 0 0.5rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.15);
}

.module-item {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.45rem 0.5rem;
  font-weight: 600;
}

.module-item:hover {
  background: rgba(255, 255, 255, 0.08);
}

.module-count {
  min-width: 1.6rem;
  padding: 0 0.4rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.15);
  font-size: 0.8rem;
  text-align: center;
}

.module-item.router-link-active .module-count {
  background: var(--blueberry);
  color: var(--sun);
}

/* open alarms: warning colour (the active entry is yellow itself, there the badge turns red) */
.module-count.is-alert {
  background: var(--sun);
  color: var(--blueberry);
  font-weight: 700;
}

.module-item.router-link-active .module-count.is-alert {
  background: var(--error);
  color: #fff;
}

.content {
  overflow: auto;
  padding: 1.5rem;
  /* wide content (charts, tables) must not widen the grid column */
  min-width: 0;
}

@media (max-width: 800px) {
  .shell {
    grid-template-columns: minmax(0, 1fr);
  }

  /* fits 390 px wide phones */
  .top {
    gap: 0.5rem;
    padding: 0 0.75rem;
    min-width: 0;
  }

  .brand img {
    height: 26px;
  }

  .menu-btn {
    display: block;
  }

  .brand span {
    display: none;
  }

  .side {
    display: none;
    position: fixed;
    top: var(--header-h);
    bottom: 0;
    left: 0;
    width: min(85vw, var(--sidebar-w));
    z-index: 20;
  }

  .menu-open .side {
    display: block;
  }

  .content {
    padding: 1rem;
  }
}
</style>
