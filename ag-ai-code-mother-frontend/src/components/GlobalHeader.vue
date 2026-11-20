<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { App, type MenuProps } from 'ant-design-vue'
import { CodeOutlined, DownOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { userLogout } from '@/api/userController'
import { resolveAvatarUrl } from '@/config/env'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const { message } = App.useApp()

const user = computed(() => loginUserStore.loginUser)
const isAdmin = computed(() => user.value.userRole === 'admin')
const isAuthPage = computed(() => route.path === '/user/login' || route.path === '/user/register')

const adminLinks = [
  { label: '用户管理', path: '/admin/userManage' },
  { label: '应用管理', path: '/admin/appManage' },
  { label: '对话管理', path: '/admin/chatManage' },
]

const onAccountAction: MenuProps['onClick'] = async ({ key }) => {
  if (key === 'profile') {
    await router.push('/user/profile')
    return
  }

  try {
    const { data } = await userLogout()
    if (data.code !== 0) {
      message.error(data.message || '退出登录失败')
      return
    }
    loginUserStore.setLoginUser({ userName: '未登录' })
    message.success('退出登录成功')
    await router.push('/user/login')
  } catch {
    message.error('退出登录失败，请重试')
  }
}
</script>

<template>
  <header class="site-header workspace-page glass-panel">
    <RouterLink to="/" class="brand" aria-label="AI 零码首页">
      <span class="brand-mark"><CodeOutlined /></span>
      <span>AI 零码</span>
    </RouterLink>

    <nav v-if="!isAuthPage" class="site-nav" aria-label="主导航">
      <RouterLink to="/" :aria-current="route.path === '/' && (!route.hash || route.hash === '#create') ? 'page' : undefined"
        >创作台</RouterLink
      >
      <RouterLink
        v-if="user.id"
        to="/#my-apps"
        :aria-current="route.path === '/' && route.hash === '#my-apps' ? 'location' : undefined"
        >我的作品</RouterLink
      >
      <RouterLink
        to="/#featured"
        :aria-current="route.path === '/' && route.hash === '#featured' ? 'location' : undefined"
        >精选案例</RouterLink
      >
      <RouterLink
        v-for="item in isAdmin ? adminLinks : []"
        :key="item.path"
        :to="item.path"
        :aria-current="route.path === item.path ? 'page' : undefined"
        >{{ item.label }}</RouterLink
      >
    </nav>

    <div class="site-account">
      <a-dropdown v-if="user.id" :trigger="['click']" placement="bottomRight">
        <button class="account-button" type="button" aria-label="账号菜单" aria-haspopup="menu">
          <a-avatar :src="resolveAvatarUrl(user.userAvatar)" :size="28">
            {{ (user.userName || user.userAccount || '用').slice(0, 1) }}
          </a-avatar>
          <span class="account-name">{{ user.userName || user.userAccount || '用户' }}</span>
          <DownOutlined class="account-chevron" />
        </button>
        <template #overlay>
          <a-menu @click="onAccountAction">
            <a-menu-item key="profile">
              <template #icon><UserOutlined /></template>
              个人主页
            </a-menu-item>
            <a-menu-item key="logout">
              <template #icon><LogoutOutlined /></template>
              退出登录
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
      <template v-else>
        <RouterLink
          class="account-link"
          to="/user/login"
          :aria-current="route.path === '/user/login' ? 'page' : undefined"
          >登录</RouterLink
        >
        <RouterLink
          class="account-link account-link--primary"
          to="/user/register"
          :aria-current="route.path === '/user/register' ? 'page' : undefined"
          >注册</RouterLink
        >
      </template>
    </div>
  </header>
</template>

<style scoped>
.site-header {
  position: sticky;
  top: 14px;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 16px;
  padding: 9px 11px 9px 13px;
  border-radius: 22px;
}

.brand,
.site-account,
.site-nav,
.account-button {
  display: flex;
  align-items: center;
}

.brand {
  gap: 11px;
  color: var(--text);
  font-size: 17px;
  font-weight: 750;
  white-space: nowrap;
}

.brand-mark {
  display: grid;
  width: 39px;
  height: 39px;
  place-items: center;
  border: 1px solid #ffffff44;
  border-radius: 13px;
  color: #fff;
  background: linear-gradient(145deg, #536071, #222b39);
  box-shadow:
    inset 0 1px 1px #ffffff38,
    0 10px 18px -12px #292f39;
}

.site-nav {
  min-width: 0;
  flex-wrap: wrap;
  justify-content: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--glass-edge);
  border-radius: 20px;
  background: #f4f6fa;
}

.site-nav a,
.account-link {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  justify-content: center;
  padding-inline: 13px;
  border-radius: 16px;
  color: var(--text-dim);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  transition:
    background 180ms ease,
    color 180ms ease;
}

.site-nav a:hover,
.account-link:hover {
  color: var(--accent);
}
.site-nav a[aria-current] {
  background: #fff;
  box-shadow: 0 3px 10px -6px #40516d75;
  color: var(--accent);
}

.site-account {
  justify-content: flex-end;
  gap: 4px;
  margin-left: auto;
}
.site-header:has(.site-nav) .site-account {
  margin-left: 0;
}
.account-link--primary {
  background: var(--accent);
  color: #fff;
}
.account-link--primary:hover {
  background: var(--accent-hover);
  color: #fff;
}

.account-button {
  gap: 8px;
  padding: 3px 8px 3px 3px;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: #fff;
  color: var(--text);
  cursor: pointer;
}

.account-button :deep(.ant-avatar) {
  background: #e4eaf3;
  color: #344357;
}
.account-name {
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.account-chevron {
  color: var(--text-faint);
  font-size: 10px;
}

@media (max-width: 920px) {
  .site-nav a {
    padding-inline: 9px;
  }
}

@media (max-width: 760px) {
  .site-header {
    position: relative;
    top: auto;
    margin-top: 10px;
  }
  .site-nav {
    order: 3;
    width: 100%;
  }
  .site-nav a {
    min-height: 32px;
  }
  .site-account {
    margin-left: auto;
  }
}
</style>
