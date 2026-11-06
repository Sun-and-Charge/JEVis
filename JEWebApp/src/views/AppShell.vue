<!-- SPDX-License-Identifier: GPL-3.0-only · Copyright (C) 2026 Sun and Charge Germany GmbH -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { localName } from '../api/navigation'
import LanguageSwitch from '../components/LanguageSwitch.vue'
import NavTree from '../components/NavTree.vue'
import { useAuthStore } from '../stores/auth'
import { useNavigationStore } from '../stores/navigation'

const { t, locale } = useI18n()
const auth = useAuthStore()
const nav = useNavigationStore()
const router = useRouter()
const route = useRoute()
const menuOpen = ref(false)
const userMenuOpen = ref(false)

onMounted(() => {
  if (!nav.data) nav.load()
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
        <p v-if="!nav.counts.analyses && !nav.counts.dashboards" class="side-info">{{ t('nav.empty') }}</p>
      </template>
    </nav>

    <main id="main" class="content" tabindex="-1">
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
