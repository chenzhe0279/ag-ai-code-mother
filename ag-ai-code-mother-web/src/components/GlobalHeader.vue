<template>
  <a-layout-header class="header">
    <div class="header-inner">
      <div class="logo" @click="router.push('/')">
        <span class="logo-icon">🤖</span>
        <span class="logo-text">AG AI Code Mother</span>
      </div>

      <nav class="nav">
        <router-link to="/" class="nav-item">应用广场</router-link>
        <router-link v-if="userStore.loginUser" to="/my" class="nav-item">我的应用</router-link>
        <router-link v-if="isAdmin" to="/admin" class="nav-item">管理后台</router-link>
      </nav>

      <div class="actions">
        <template v-if="userStore.loginUser">
          <a-dropdown>
            <a class="user-info">
              <a-avatar size="small">{{ avatarText }}</a-avatar>
              <span>{{ userStore.loginUser.userName || userStore.loginUser.userAccount }}</span>
            </a>
            <template #overlay>
              <a-menu>
                <a-menu-item key="profile" disabled>
                  {{ userStore.loginUser.userRole === 'admin' ? '管理员' : '普通用户' }}
                </a-menu-item>
                <a-menu-item key="logout" @click="handleLogout">退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <template v-else>
          <a-button type="link" @click="router.push('/login')">登录</a-button>
          <a-button type="primary" @click="router.push('/register')">注册</a-button>
        </template>
      </div>
    </div>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const isAdmin = computed(() => userStore.loginUser?.userRole === 'admin')
const avatarText = computed(() =>
  (userStore.loginUser?.userName || userStore.loginUser?.userAccount || 'U').slice(0, 1),
)

const handleLogout = async () => {
  await userStore.logout()
  router.push('/')
}
</script>

<style scoped>
.header {
  background: #001529;
  padding: 0 24px;
  height: 64px;
  line-height: 64px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  height: 64px;
}

.logo {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: #fff;
  font-weight: 600;
  font-size: 18px;
  gap: 8px;
}

.logo-icon {
  font-size: 22px;
}

.nav {
  flex: 1;
  margin-left: 40px;
  display: flex;
  gap: 24px;
}

.nav-item {
  color: rgba(255, 255, 255, 0.75);
  text-decoration: none;
  transition: color 0.2s;
}

.nav-item:hover,
.router-link-active {
  color: #fff;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-info {
  color: rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
</style>
