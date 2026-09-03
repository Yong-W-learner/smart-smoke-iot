import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/visitor-alert' },
  { path: '/visitor-alert', component: () => import('@/views/forest/VisitorAlert.vue') },
  { path: '/login', component: () => import('@/views/Login.vue') },
  { path: '/ranger', component: () => import('@/views/forest/RangerHome.vue') },
  { path: '/mobile', component: () => import('@/views/forest/RangerMobile.vue') },
  { path: '/ranger/equipment', component: () => import('@/views/forest/ForestMaintenance.vue') },
  { path: '/ranger/ai', component: () => import('@/views/forest/AiAssistant.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/visitor-alert' }
]

const router = createRouter({ history: createWebHistory(), routes })

function currentRole() {
  try { return JSON.parse(localStorage.getItem('currentUser') || '{}').role || '' } catch { return '' }
}

router.beforeEach((to) => {
  if (['/visitor-alert', '/login'].includes(to.path)) return true
  if (!localStorage.getItem('token')) return '/login'
  const role = currentRole()
  if (to.path.startsWith('/ranger') || to.path.startsWith('/mobile')) return (role === 'ranger' || role === 'admin') ? true : '/login'
  return true
})

export default router
