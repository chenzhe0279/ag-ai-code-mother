<template>
  <a-card hoverable class="app-card" @click="goDetail">
    <div class="app-card-body">
      <div class="app-avatar">{{ avatarText }}</div>
      <div class="app-info">
        <div class="app-name">{{ app.appName || '未命名应用' }}</div>
        <div class="app-desc">{{ app.initPrompt || '暂无描述' }}</div>
        <div class="app-meta">
          <span v-if="app.currentVersion">v{{ app.currentVersion }}</span>
          <span v-if="app.user?.userName"> · {{ app.user.userName }}</span>
          <span> · {{ (app.createTime || '').slice(0, 10) }}</span>
        </div>
      </div>
    </div>
  </a-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { AppVO } from '@/api/app'

const props = defineProps<{ app: AppVO }>()
const router = useRouter()

const avatarText = computed(() => (props.app.appName || 'A').slice(0, 1))

const goDetail = () => {
  router.push(`/app/${props.app.id}`)
}
</script>

<style scoped>
.app-card {
  border-radius: 10px;
  overflow: hidden;
}

.app-card-body {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.app-avatar {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  background: linear-gradient(135deg, #1677ff, #69b1ff);
  color: #fff;
  font-size: 22px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.app-info {
  flex: 1;
  min-width: 0;
}

.app-name {
  font-size: 16px;
  font-weight: 600;
}

.app-desc {
  color: #666;
  font-size: 13px;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.app-meta {
  color: #999;
  font-size: 12px;
  margin-top: 8px;
}
</style>
