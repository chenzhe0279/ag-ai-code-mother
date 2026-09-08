<template>
  <div class="auth-page">
    <div class="auth-card reveal">
      <p class="eyebrow">CREATE ACCOUNT</p>
      <h2>创建你的账号</h2>
      <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
        <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
          <a-input v-model:value="formState.userAccount" placeholder="请输入账号" size="large" />
        </a-form-item>
        <a-form-item
          name="userPassword"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码不能小于 8 位' },
          ]"
        >
          <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" size="large" />
        </a-form-item>
        <a-form-item
          name="checkPassword"
          :rules="[
            { required: true, message: '请确认密码' },
            { min: 8, message: '密码不能小于 8 位' },
            { validator: validateCheckPassword },
          ]"
        >
          <a-input-password v-model:value="formState.checkPassword" placeholder="请确认密码" size="large" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block size="large" class="auth-submit">
            注册 →
          </a-button>
        </a-form-item>
      </a-form>
      <p class="auth-switch">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { reactive } from 'vue'

const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

/**
 * 验证确认密码
 * @param rule
 * @param value
 * @param callback
 */
const validateCheckPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: API.UserRegisterRequest) => {
  const res = await userRegister(values)
  // 注册成功，跳转到登录页面
  if (res.data.code === 0) {
    message.success('注册成功')
    router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<style scoped>
.auth-page {
  width: min(100%, 1120px);
  min-height: calc(100vh - 64px);
  margin: 0 auto;
  padding: 29px 0 24px;
  display: flex;
  flex-direction: column;
}

.auth-card {
  width: min(100%, 440px);
  margin: auto;
  padding: 36px 34px 30px;
  border: 1px solid rgba(164, 184, 232, 0.22);
  border-radius: 22px;
  background: rgba(22, 31, 60, 0.42);
  box-shadow: inset 0 1px rgba(255, 255, 255, 0.05), 0 24px 70px rgba(2, 5, 17, 0.35);
  backdrop-filter: blur(12px);
  display: grid;
  gap: 6px;
}

.auth-card h2 {
  margin: 0 0 16px;
  font: 600 24px/1.3 'Playfair Display', 'Noto Sans SC', serif;
  color: #f2f5ff;
}

.auth-card :deep(.eyebrow) {
  margin-bottom: 6px;
}

.auth-submit {
  background: linear-gradient(
    135deg,
    rgba(142, 162, 255, 0.55),
    rgba(109, 124, 255, 0.5) 48%,
    rgba(165, 95, 224, 0.55)
  );
  border: none;
  box-shadow: 0 5px 15px rgba(95, 106, 196, 0.25);
}

.auth-switch {
  margin: 4px 0 0;
  text-align: center;
  color: #8c9abc;
  font-size: 13px;
}

.auth-switch a {
  color: #b7c6ff;
}

.auth-switch a:hover {
  color: #e4eaff;
}
</style>
