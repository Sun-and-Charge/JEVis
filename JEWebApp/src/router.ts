/* SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 Sun and Charge Germany GmbH */
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from './stores/auth'

export const router = createRouter({
  history: createWebHistory('/app/'),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { public: true } },
    { path: '/forgot', name: 'forgot', component: () => import('./views/ForgotPasswordView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('./views/AppShell.vue'),
      children: [
        { path: '', name: 'home', component: () => import('./views/HomeView.vue') },
        { path: 'password', name: 'password', component: () => import('./views/ChangePasswordView.vue') },
        { path: 'analyses/:id(\\d+)', name: 'analysis', component: () => import('./views/AnalysisView.vue'), props: (r) => ({ id: Number(r.params.id) }) },
        { path: 'dashboards/:id(\\d+)', name: 'dashboard', component: () => import('./views/DashboardView.vue'), props: (r) => ({ id: Number(r.params.id) }) },
        { path: 'appointments', name: 'appointments', component: () => import('./views/AppointmentsView.vue') },
        { path: 'maintenance', name: 'maintenance', component: () => import('./views/MaintenanceView.vue') },
        { path: 'actionplans', name: 'actionPlans', component: () => import('./views/ActionPlansView.vue') },
        { path: 'nonconformities', name: 'nonconformities', component: () => import('./views/NonconformitiesView.vue') },
        { path: 'legal', name: 'legal', component: () => import('./views/LegalView.vue') },
        { path: 'meters', name: 'meters', component: () => import('./views/RegisterView.vue'), props: { kind: 'meters' } },
        { path: 'basedata', name: 'basedata', component: () => import('./views/RegisterView.vue'), props: { kind: 'basedata' } },
        { path: 'equipment', name: 'equipment', component: () => import('./views/RegisterView.vue'), props: { kind: 'equipment' } },
        { path: ':pathMatch(.*)*', name: 'notFound', component: () => import('./views/NotFoundView.vue') },
      ],
    },
  ],
})

let restored = false

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!restored) {
    restored = true
    await auth.restore()
  }
  if (to.meta.public) {
    return auth.isLoggedIn && to.name === 'login' ? { name: 'home' } : true
  }
  if (!auth.isLoggedIn) {
    return { name: 'login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }
  if (auth.mustChangePassword && to.name !== 'password') {
    return { name: 'password' }
  }
  return true
})
