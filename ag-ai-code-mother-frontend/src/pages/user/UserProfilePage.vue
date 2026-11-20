<template>
  <div class="profile-page workspace-page">
    <header class="page-heading"><h1>个人主页</h1></header>
    <div class="profile-card glass-panel">
      <div class="profile-head">
        <div class="avatar-upload-wrap">
          <a-upload
            name="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            :show-upload-list="false"
            :before-upload="beforeAvatarUpload"
            :custom-request="handleAvatarUpload"
            :disabled="avatarUploading"
          >
            <div
              class="avatar-uploader"
              :class="{ 'is-uploading': avatarUploading }"
              title="点击上传头像"
            >
              <a-avatar :src="avatarPreviewUrl" :size="72">
                {{ (formData.userName || 'U').charAt(0) }}
              </a-avatar>
              <span class="avatar-overlay" aria-hidden="true">
                <LoadingOutlined v-if="avatarUploading" spin />
                <CameraOutlined v-else />
              </span>
            </div>
          </a-upload>
          <span class="avatar-tip">点击头像上传</span>
        </div>
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
          <a-input
            v-model:value="formData.userName"
            placeholder="请输入昵称"
            :maxlength="30"
            show-count
          />
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
            <a-button
              type="primary"
              html-type="submit"
              :loading="submitting"
              :disabled="avatarUploading"
            >
              保存修改
            </a-button>
            <a-button :disabled="avatarUploading" @click="resetForm">重置</a-button>
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
import type { FormInstance, UploadProps } from 'ant-design-vue'
import { CameraOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { updateMyUser, uploadUserAvatar } from '@/api/userController'
import { resolveAvatarUrl } from '@/config/env'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const avatarUploading = ref(false)

// 表单只包含允许用户自己修改的字段（账号、角色由后端控制，不放在这里）
const formData = reactive({
  userName: '',
  userAvatar: '',
  userProfile: '',
})

const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')
const avatarPreviewUrl = computed(() => resolveAvatarUrl(formData.userAvatar))

const rules = {
  userName: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 1, max: 30, message: '昵称长度需在 1-30 个字符之间', trigger: 'blur' },
  ],
}

// 用当前登录态回填表单
const resetForm = () => {
  const user = loginUserStore.loginUser
  formData.userName = user.userName ?? ''
  formData.userAvatar = user.userAvatar ?? ''
  formData.userProfile = user.userProfile ?? ''
  formRef.value?.clearValidate()
}

const beforeAvatarUpload: UploadProps['beforeUpload'] = (file) => {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
  if (!allowedTypes.includes(file.type)) {
    message.error('仅支持 JPG、PNG、WebP、GIF 格式的图片')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    message.error('头像图片不能超过 5MB')
    return false
  }
  return true
}

const handleAvatarUpload: UploadProps['customRequest'] = async (options) => {
  const file = options.file as File
  avatarUploading.value = true
  try {
    const res = await uploadUserAvatar(file)
    if (res.data.code === 0 && res.data.data) {
      formData.userAvatar = res.data.data
      message.success('头像上传成功，请点击保存修改')
      options.onSuccess?.(res.data)
    } else {
      const error = new Error(res.data.message || '头像上传失败')
      message.error(error.message)
      options.onError?.(error)
    }
  } catch (error) {
    console.error('头像上传失败：', error)
    message.error('头像上传失败，请重试')
    options.onError?.(error as Error)
  } finally {
    avatarUploading.value = false
  }
}

const handleSubmit = async () => {
  if (avatarUploading.value) {
    message.warning('头像上传中，请稍后再保存')
    return
  }
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
  width: min(620px, var(--workspace-width));
  padding-block: clamp(30px, 5vw, 66px) 15px;
}

.profile-card {
  padding: clamp(18px, 3vw, 32px);
}
.profile-head {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 24px;
}
.profile-meta {
  min-width: 0;
  overflow-wrap: anywhere;
}
.profile-meta h2 {
  margin: 0 0 4px;
  color: var(--text);
  font-size: 21px;
  font-weight: 700;
}
.profile-account,
.avatar-tip {
  color: var(--text-faint);
  font-size: 12px;
}

.avatar-upload-wrap {
  flex: none;
  display: grid;
  justify-items: center;
  gap: 7px;
}
.avatar-upload-wrap :deep(.ant-upload) {
  display: block;
  cursor: pointer;
}
.avatar-uploader {
  position: relative;
  overflow: hidden;
  width: 72px;
  height: 72px;
  border-radius: 50%;
}
.avatar-uploader :deep(.ant-avatar) {
  display: block;
  background: #e4eaf3;
  color: var(--text-dim);
}
.avatar-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: #242b38ba;
  color: #fff;
  font-size: 20px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 180ms ease;
}
.avatar-uploader:hover .avatar-overlay,
.avatar-upload-wrap:focus-within .avatar-overlay,
.avatar-uploader.is-uploading .avatar-overlay {
  opacity: 1;
}
.avatar-uploader.is-uploading {
  cursor: wait;
}
</style>
