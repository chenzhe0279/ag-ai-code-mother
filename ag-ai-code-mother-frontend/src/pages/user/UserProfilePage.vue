<template>
  <div class="profile-page">
    <div class="profile-card reveal">
      <div class="profile-head">
        <a-avatar :src="formData.userAvatar" :size="72">
          {{ (formData.userName || 'U').charAt(0) }}
        </a-avatar>
        <div class="profile-meta">
          <h2>{{ formData.userName || '未命名用户' }}</h2>
          <p class="profile-account">{{ loginUserStore.loginUser.userAccount || '-' }}</p>
          <a-tag v-if="isAdmin" color="purple">管理员</a-tag>
          <a-tag v-else color="blue">普通用户</a-tag>
        </div>
      </div>

      <a-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        layout="vertical"
        @finish="handleSubmit"
      >
        <a-form-item label="昵称" name="userName">
          <a-input v-model:value="formData.userName" placeholder="请输入昵称" :maxlength="30" show-count />
        </a-form-item>
        <a-form-item label="头像地址" name="userAvatar" extra="填入图片链接，留空则显示昵称首字母">
          <a-input v-model:value="formData.userAvatar" placeholder="https://..." />
        </a-form-item>
        <a-form-item label="个人简介" name="userProfile">
          <a-textarea
            v-model:value="formData.userProfile"
            placeholder="介绍一下自己吧"
            :rows="4"
            :maxlength="200"
            show-count
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit" :loading="submitting">保存修改</a-button>
            <a-button @click="resetForm">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { updateMyUser } from '@/api/userController'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

// 表单只包含允许用户自己修改的字段（账号、角色由后端控制，不放在这里）
const formData = reactive({
  userName: '',
  userAvatar: '',
  userProfile: '',
})

const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

const rules = {
  userName: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 1, max: 30, message: '昵称长度需在 1-30 个字符之间', trigger: 'blur' },
  ],
  userAvatar: [{ type: 'url', message: '请输入有效的图片链接', trigger: 'blur' }],
}

// 用当前登录态回填表单
const resetForm = () => {
  const user = loginUserStore.loginUser
  formData.userName = user.userName ?? ''
  formData.userAvatar = user.userAvatar ?? ''
  formData.userProfile = user.userProfile ?? ''
  formRef.value?.clearValidate()
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    const res = await updateMyUser({
      userName: formData.userName,
      userAvatar: formData.userAvatar,
      userProfile: formData.userProfile,
    })
    if (res.data.code === 0) {
      message.success('保存成功')
      // 重新拉取登录态，让顶部导航的头像与昵称同步刷新
      await loginUserStore.fetchLoginUser()
      resetForm()
    } else {
      message.error('保存失败：' + res.data.message)
    }
  } catch (error) {
    console.error('保存失败：', error)
    message.error('保存失败，请重试')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  // 未登录直接回登录页
  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    router.push('/user/login')
    return
  }
  resetForm()
})
</script>

<style scoped>
.profile-page {
  width: min(100%, 1120px);
  min-height: calc(100vh - 64px);
  margin: 0 auto;
  padding: 29px 24px 24px;
  display: flex;
  flex-direction: column;
}

.profile-card {
  width: min(100%, 560px);
  margin: auto;
  padding: 36px 34px 30px;
  border: 1px solid rgba(164, 184, 232, 0.22);
  border-radius: 22px;
  background: rgba(22, 31, 60, 0.42);
  box-shadow: inset 0 1px rgba(255, 255, 255, 0.05), 0 24px 70px rgba(2, 5, 17, 0.35);
  backdrop-filter: blur(12px);
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 26px;
}

.profile-meta h2 {
  margin: 0 0 6px;
  font: 600 22px/1.3 'Playfair Display', 'Noto Sans SC', serif;
  color: #f2f5ff;
}

.profile-account {
  margin: 0 0 8px;
  color: #8c9abc;
  font: 11px 'DM Mono', monospace;
  letter-spacing: 0.06em;
}
</style>