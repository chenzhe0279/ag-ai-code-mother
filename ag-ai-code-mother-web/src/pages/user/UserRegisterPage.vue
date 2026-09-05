<template>
  <div class="register-page">
    <a-card class="register-card" :bordered="false">
      <div class="register-title">
        <h2>创建账号</h2>
        <p>注册 AG AI Code Mother</p>
      </div>
      <a-form :model="form" @finish="handleRegister">
        <a-form-item
          name="userAccount"
          :rules="[
            { required: true, message: '请输入账号' },
            { min: 4, message: '账号至少 4 位' },
          ]"
        >
          <a-input v-model:value="form.userAccount" placeholder="账号（至少 4 位）" size="large" />
        </a-form-item>
        <a-form-item
          name="userPassword"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码至少 8 位' },
          ]"
        >
          <a-input-password v-model:value="form.userPassword" placeholder="密码（至少 8 位）" size="large" />
        </a-form-item>
        <a-form-item
          name="checkPassword"
          :rules="checkPasswordRules"
        >
          <a-input-password v-model:value="form.checkPassword" placeholder="确认密码" size="large" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" block :loading="loading">
            注册
          </a-button>
        </a-form-item>
      </a-form>
      <div class="register-footer">
        <span>已有账号？</span>
        <a-button type="link" @click="router.push('/login')">去登录</a-button>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { userRegister } from '@/api/user'
import type { Rule } from 'ant-design-vue/es/form'

const router = useRouter()
const loading = ref(false)
const form = reactive({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const checkPasswordRules: Rule[] = [
  { required: true, message: '请确认密码' },
  {
    validator: (_rule, value) =>
      value === form.userPassword ? Promise.resolve() : Promise.reject('两次密码不一致'),
  },
]

const handleRegister = async () => {
  loading.value = true
  try {
    await userRegister(form)
    message.success('注册成功，请登录')
    router.push('/login')
  } catch {
    // 错误已在拦截器中提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  min-height: calc(100vh - 160px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.register-card {
  width: 400px;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.register-title {
  text-align: center;
  margin-bottom: 24px;
}

.register-title p {
  color: #999;
  margin-top: 4px;
}

.register-footer {
  text-align: center;
  color: #666;
}
</style>
