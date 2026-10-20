import { createRouter, createWebHistory } from 'vue-router'
import { currentUser, token, type UserInfo } from '../api/auth'

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
        { path: 'dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { roles: ['system_admin'] } },
        { path: 'bookings', name: 'live-bookings', component: () => import('../views/LiveBookingsView.vue'), meta: { roles: ['system_admin', 'live_admin'] } },
        { path: 'basic-info', name: 'basic-info', component: () => import('../views/BasicInfoView.vue'), meta: { roles: ['system_admin'] } },
        { path: 'accounts', name: 'accounts', component: () => import('../views/AccountsView.vue'), meta: { roles: ['system_admin'] } },
        { path: 'farmer/bookings', name: 'farmer-bookings', component: () => import('../views/FarmerBookingsView.vue'), meta: { roles: ['farmer'] } },
        { path: 'farmer/bookings/new', name: 'farmer-booking-new', component: () => import('../views/NewBookingView.vue'), meta: { roles: ['farmer'] } },
        { path: 'todo', name: 'todo', component: () => import('../views/NotImplementedView.vue') },
        { path: 'farmers', redirect: '/basic-info' },
        { path: 'venues', redirect: '/basic-info' },
        { path: 'devices', redirect: '/basic-info' }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

function homeFor(user: UserInfo | null): string {
  if (!user) return '/login'
  if (user.mustChangePassword) return '/change-password'
  if (user.roleCode === 'farmer') return '/farmer/bookings'
  if (user.roleCode === 'live_admin') return '/bookings'
  return '/dashboard'
}

router.beforeEach((to) => {
  const hasToken = Boolean(token())
  const user = currentUser()

  if (to.path === '/login') {
    if (!hasToken) return true
    return homeFor(user)
  }

  if (!hasToken) return '/login'

  if (user?.mustChangePassword && to.path !== '/change-password') {
    return '/change-password'
  }

  if (!user?.mustChangePassword && to.path === '/change-password') {
    return homeFor(user)
  }

  const roles = to.meta.roles as string[] | undefined
  if (roles && user && !roles.includes(user.roleCode)) {
    return homeFor(user)
  }

  return true
})

export default router
