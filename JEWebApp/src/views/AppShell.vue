<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { startDashboardFor } from '../api/navigation'
import LanguageSwitch from '../components/LanguageSwitch.vue'
import MaintenanceBanner from '../components/MaintenanceBanner.vue'
import CommandPalette from '../components/nav/CommandPalette.vue'
import NavIcon from '../components/nav/NavIcon.vue'
import SideNav from '../components/nav/SideNav.vue'
import SitePicker from '../components/nav/SitePicker.vue'
import { useNarrow } from '../modules/useNarrow'
import { openedItemKey } from '../nav/catalog'
import { isPaletteShortcut } from '../nav/palette'
import { useAuthStore } from '../stores/auth'
import { useNavigationStore } from '../stores/navigation'
import { useNavPrefsStore } from '../stores/navPrefs'

const { t } = useI18n()
const auth = useAuthStore()
const nav = useNavigationStore()
const prefs = useNavPrefsStore()
const router = useRouter()
const route = useRoute()
const narrow = useNarrow()
const menuOpen = ref(false)
const userMenuOpen = ref(false)
const paletteOpen = ref(false)

/* icon rail only on wide screens; on phones the sidebar is a drawer */
const rail = computed(() => prefs.collapsed && !narrow.value)

/* after login (the shell is mounted once per session): the user's start dashboard instead of the home page */
onMounted(async () => {
  window.addEventListener('keydown', onGlobalKey)
  if (nav.data) return
  await nav.load()
  const start = startDashboardFor(nav.data, route.name)
  if (start != null) router.replace({ name: 'dashboard', params: { id: start } })
})

onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKey))

/* Strg+K / Cmd+K, or "/" outside text fields: the search */
function onGlobalKey(e: KeyboardEvent) {
  if (paletteOpen.value || !isPaletteShortcut(e)) return
  e.preventDefault()
  openPalette()
}

function openPalette() {
  menuOpen.value = false
  userMenuOpen.value = false
  paletteOpen.value = true
}

/* "Zuletzt geöffnet": every opened dashboard / analysis */
watch(
  () => openedItemKey(route.name, route.params),
  (key) => {
    if (key) prefs.recordOpened(key)
  },
  { immediate: true },
)

/* session ended (logout or expired token) while the app is open */
watch(
  () => auth.isLoggedIn,
  (loggedIn) => {
    if (!loggedIn) {
      paletteOpen.value = false
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
  <div class="shell" :class="{ 'menu-open': menuOpen, rail }">
    <a href="#main" class="visually-hidden">{{ t('app.skip') }}</a>
    <header class="top">
      <button type="button" class="menu-btn" :aria-label="t('header.menu')" :aria-expanded="menuOpen" aria-controls="side-nav" @click="menuOpen = !menuOpen">☰</button>
      <RouterLink :to="{ name: 'home' }" class="brand">
        <img src="/suc_logo_dark.png" alt="Sun and Charge" />
        <span>{{ t('app.name') }}</span>
      </RouterLink>
      <SitePicker v-if="nav.data" class="top-site" />
      <div class="spacer" />
      <button v-if="nav.data" type="button" class="search-btn" aria-haspopup="dialog" :aria-label="t('nav.searchLabel') + ' (' + t('nav.shortcut') + ')'" @click="openPalette">
        <NavIcon name="search" />
        <span class="search-text" aria-hidden="true">{{ t('nav.search') }}</span>
        <kbd class="search-kbd" aria-hidden="true">{{ t('nav.shortcut') }}</kbd>
      </button>
      <LanguageSwitch />
      <div class="user">
        <button type="button" class="user-btn" :aria-label="auth.user?.name" :aria-expanded="userMenuOpen" aria-haspopup="menu" @click="userMenuOpen = !userMenuOpen">
          <NavIcon name="user" class="user-icon" />
          <span class="user-name">{{ auth.user?.name }}</span> ▾
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

    <nav id="side-nav" class="side" :aria-label="t('nav.main')">
      <SitePicker v-if="nav.data" class="drawer-site" />
      <p v-if="nav.loading" class="side-info">{{ t('nav.loading') }}</p>
      <p v-else-if="nav.failed" class="side-info">{{ t('nav.error') }}</p>
      <SideNav v-else-if="nav.data" :rail="rail" @navigate="menuOpen = false" @expand="prefs.setCollapsed(false)" />
    </nav>

    <main id="main" class="content" tabindex="-1">
      <MaintenanceBanner v-if="nav.data" />
      <RouterView />
    </main>

    <CommandPalette v-if="paletteOpen" @close="paletteOpen = false" />
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: var(--sidebar-w) 1fr;
  grid-template-rows: var(--header-h) 1fr;
  height: 100%;
}

/* collapsed sidebar: icon rail */
.shell.rail {
  grid-template-columns: 60px 1fr;
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

.rail .side {
  padding: 0.75rem 0.35rem;
}

.side-info {
  opacity: 0.8;
  padding: 0.5rem;
}

.search-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  padding: 0.35rem 0.6rem;
  font: inherit;
  font-size: 0.9rem;
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 4px;
  cursor: pointer;
}

.search-btn:hover {
  background: rgba(255, 255, 255, 0.18);
}

.search-text {
  opacity: 0.85;
  min-width: 7rem;
  text-align: left;
}

.search-kbd {
  font: inherit;
  font-size: 0.72rem;
  padding: 0 0.35rem;
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 3px;
  opacity: 0.85;
}

.content {
  overflow: auto;
  padding: 1.5rem;
  /* wide content (charts, tables) must not widen the grid column */
  min-width: 0;
}

/* the site picker sits in the top bar on wide screens and at the top of the drawer on phones */
.drawer-site,
.user-icon {
  display: none;
}

@media (max-width: 800px) {
  .top-site,
  .user-name {
    display: none;
  }

  .user-icon {
    display: inline-block;
    vertical-align: middle;
  }

  .drawer-site {
    display: block;
    padding: 0.75rem 1rem 0.25rem;
  }

  .drawer-site :deep(.site-label) {
    position: static;
    width: auto;
    height: auto;
    overflow: visible;
    clip: auto;
    display: block;
    margin-bottom: 0.25rem;
  }

  .drawer-site :deep(select) {
    max-width: none;
    width: 100%;
  }

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

  .search-text,
  .search-kbd {
    display: none;
  }

  .search-btn {
    padding: 0.35rem;
    border: none;
    background: none;
  }

  .content {
    padding: 1rem;
  }
}
</style>
