<template>
  <div class="auth-page">
    <div class="auth-card reveal">
      <p class="eyebrow">WELCOME BACK</p>
      <h2>欢迎回来</h2>
      <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
        <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
          <a-input v-model:value="formState.userAccount" placeholder="请输入账号" size="large" />
        </a-form-item>
        <a-form-item
          name="userPassword"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码长度不能小于 8 位' },
          ]"
        >
          <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" size="large" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block size="large" class="auth-submit">
            登录 →
          </a-button>
        </a-form-item>
      </a-form>
      <p class="auth-switch">
        还没有账号？
        <RouterLink to="/user/register">去注册</RouterLink>
      </p>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { reactive } from 'vue'
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const router = useRouter()
const loginUserStore = useLoginUserStore()

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  const res = await userLogin(values)
  // 登录成功，把登录态保存到全局状态中
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success('登录成功')
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error('登录失败，' + res.data.message)
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
