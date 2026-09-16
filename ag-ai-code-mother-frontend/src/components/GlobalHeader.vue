<template>
  <a-layout-header class="header reveal" style="--rd: 240ms">
    <div class="site-nav">
      <!-- 左侧：品牌区 -->
      <RouterLink to="/" class="brand">
        <span class="brand-mark">✦</span>
        <span class="brand-name">AI 零码</span>
        <small>AI SPACE</small>
      </RouterLink>
      <!-- 中间：导航菜单 -->
      <a-menu
        v-model:selectedKeys="selectedKeys"
        class="nav-menu"
        mode="horizontal"
        :items="menuItems"
        @click="handleMenuClick"
      />
      <!-- 右侧：用户操作区域 -->
      <div class="nav-user">
        <template v-if="loginUserStore.loginUser.id">
          <!-- 点击头像/昵称直接进入个人主页 -->
          <button class="nav-chip" type="button" title="个人主页" @click="goProfile">
            <span class="nav-chip-avatar">
              <img
                v-if="loginUserStore.loginUser.userAvatar"
                :src="resolveAvatarUrl(loginUserStore.loginUser.userAvatar)"
                alt=""
              />
              <span v-else>{{ (loginUserStore.loginUser.userName ?? '无')[0] }}</span>
            </span>
            <span>{{ loginUserStore.loginUser.userName ?? '无名' }}</span>
            <em v-if="loginUserStore.loginUser.userRole === 'admin'">admin</em>
          </button>
          <button class="nav-link" type="button" @click="doLogout">退出</button>
        </template>
        <template v-else>
          <a-button class="nav-link-btn" href="/user/login">登录</a-button>
          <a-button class="nav-link-btn primary" href="/user/register">注册</a-button>
        </template>
      </div>
    </div>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'
import { resolveAvatarUrl } from '@/config/env'
import { HomeOutlined } from '@ant-design/icons-vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()
// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])
// 监听路由变化，更新当前选中菜单
router.afterEach((to) => {
  selectedKeys.value = [to.path]
})

// 菜单配置项
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/appManage',
    label: '应用管理',
    title: '应用管理',
  },
  {
    key: '/admin/chatManage',
    label: '对话管理',
    title: '对话管理',
  },
]

// 过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

// 展示在菜单的路由数组
const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems))

// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  // 跳转到对应页面
  if (key.startsWith('/')) {
    router.push(key)
  }
}

// 退出登录
const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}

// 进入个人主页
const goProfile = () => {
  router.push('/user/profile')
}
</script>

<style scoped>
.header {
  padding: 0 31px;
  min-height: 64px;
  display: flex;
  align-items: center;
  line-height: normal;
}

.site-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  width: 100%;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font:
    500 13px 'DM Mono',
    monospace;
  letter-spacing: 0.13em;
  color: var(--text);
  white-space: nowrap;
}

.brand-mark {
  font-size: 20px;
  color: #e0e8ff;
  text-shadow: 0 0 22px #91b0ff;
  animation: brand-pulse 3.6s ease-in-out infinite;
}

@keyframes brand-pulse {
  50% {
    text-shadow: 0 0 34px #c3a6ff;
    transform: scale(1.08);
  }
}

.brand-name {
  font-family: 'Noto Sans SC', system-ui, sans-serif;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.brand small {
  color: #8294c4;
  font-size: 9px;
  letter-spacing: 0.16em;
}

.nav-menu {
  flex: 1;
  min-width: 0;
  justify-content: center;
}

.nav-user {
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}

.nav-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid rgba(164, 184, 232, 0.23);
  border-radius: 12px;
  padding: 4px 12px 4px 6px;
  background: rgba(17, 26, 49, 0.32);
  color: #e3e9fb;
  font-size: 13px;
  cursor: pointer;
  transition: 0.2s;
  backdrop-filter: blur(8px);
}

.nav-chip:hover {
  border-color: rgba(185, 204, 255, 0.6);
}

.nav-chip-avatar {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  overflow: hidden;
  border-radius: 9px;
  background: var(--grad-btn);
  color: #fff;
  font-weight: 700;
  font-size: 12px;
}

.nav-chip-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.nav-chip em {
  font-style: normal;
  font-size: 10px;
  color: #c9b2ff;
}

.nav-link-btn {
  border: 1px solid rgba(164, 184, 232, 0.23);
  background: rgba(17, 26, 49, 0.32);
  color: #cdd8f5;
  backdrop-filter: blur(8px);
}

/* 顶部「退出」按钮 */
.nav-link {
  border: 1px solid rgba(164, 184, 232, 0.23);
  border-radius: 10px;
  padding: 6px 14px;
  background: rgba(17, 26, 49, 0.32);
  color: #cdd8f5;
  font-size: 13px;
  cursor: pointer;
  transition: 0.2s;
  backdrop-filter: blur(8px);
}

.nav-link:hover {
  border-color: rgba(185, 204, 255, 0.6);
  color: #fff;
}

.nav-link-btn.primary {
  background: linear-gradient(
    135deg,
    rgba(142, 162, 255, 0.35),
    rgba(109, 124, 255, 0.3) 48%,
    rgba(165, 95, 224, 0.35)
  );
  border-color: transparent;
  color: #fff;
  box-shadow: 0 5px 18px rgba(109, 124, 255, 0.2);
}

@media (max-width: 780px) {
  .header {
    padding: 0 16px;
  }
  .brand small {
    display: none;
  }
}
</style>
