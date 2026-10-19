import { createRouter, createWebHistory } from 'vue-router'
import { currentUser, token } from '../api/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue')
    },
    {
      path: '/change-password',
      name: 'change-password',
      component: () => import('../views/ChangePasswordView.vue')
    },
    {
      path: '/',
      component: () => import('../layout/MainLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
        { path: 'accounts', name: 'accounts', component: () => import('../views/AccountsView.vue') },
        { path: 'farmers', name: 'farmers', component: () => import('../views/FarmersView.vue') },
        { path: 'venues', name: 'venues', component: () => import('../views/VenuesView.vue') },
        { path: 'devices', name: 'devices', component: () => import('../views/DevicesView.vue') }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach((to) => {
  const hasToken = Boolean(token())
  const user = currentUser()

  if (to.path === '/login') {
    if (!hasToken) return true
    if (user?.mustChangePassword) return '/change-password'
    return '/dashboard'
  }

  if (!hasToken) return '/login'

  if (user?.mustChangePassword && to.path !== '/change-password') {
    return '/change-password'
  }

  if (!user?.mustChangePassword && to.path === '/change-password') {
    return '/dashboard'
  }

  return true
})

export default router
