import { createRouter, createWebHistory } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/pages/HomePage.vue'),
      meta: { title: '应用广场' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/pages/user/UserLoginPage.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/pages/user/UserRegisterPage.vue'),
      meta: { title: '注册' },
    },
    {
      path: '/my',
      name: 'myapps',
      component: () => import('@/pages/my/MyAppsPage.vue'),
      meta: { title: '我的应用', requiresAuth: true },
    },
    {
      path: '/app/:id',
      name: 'appdetail',
      component: () => import('@/pages/app/AppDetailPage.vue'),
      meta: { title: '应用详情', requiresAuth: true },
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('@/pages/admin/AdminPage.vue'),
      meta: { title: '管理后台', requiresAuth: true, requiresAdmin: true },
    },
  ],
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  if (!userStore.loginUser && !userStore.userLoading) {
    await userStore.fetchLoginUser()
  }
  if (to.meta.requiresAuth && !userStore.loginUser) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && userStore.loginUser?.userRole !== 'admin') {
    message.error('需要管理员权限')
    return { name: 'home' }
  }
  document.title = (to.meta.title as string) || 'AG AI Code Mother'
  return true
})

export default router
