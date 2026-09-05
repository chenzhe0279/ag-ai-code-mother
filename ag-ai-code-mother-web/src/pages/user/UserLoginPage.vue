<template>
  <div class="login-page">
    <a-card class="login-card" :bordered="false">
      <div class="login-title">
        <h2>欢迎回来</h2>
        <p>登录 AG AI Code Mother</p>
      </div>
      <a-form :model="form" @finish="handleLogin">
        <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
          <a-input v-model:value="form.userAccount" placeholder="账号" size="large">
            <template #prefix><span>👤</span></template>
          </a-input>
        </a-form-item>
        <a-form-item name="userPassword" :rules="[{ required: true, message: '请输入密码' }]">
          <a-input-password v-model:value="form.userPassword" placeholder="密码" size="large">
            <template #prefix><span>🔒</span></template>
          </a-input-password>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" block :loading="loading">
            登录
          </a-button>
        </a-form-item>
      </a-form>
      <div class="login-footer">
        <span>还没有账号？</span>
        <a-button type="link" @click="router.push('/register')">立即注册</a-button>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const form = reactive({
  userAccount: '',
  userPassword: '',
})

const handleLogin = async () => {
  loading.value = true
  try {
    const user = await userStore.login(form.userAccount, form.userPassword)
    message.success(`欢迎回来，${user.userName || user.userAccount}`)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch {
    // 错误已在拦截器中提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: calc(100vh - 160px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 400px;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.login-title {
  text-align: center;
  margin-bottom: 24px;
}

.login-title p {
  color: #999;
  margin-top: 4px;
}

.login-footer {
  text-align: center;
  color: #666;
}
</style>
