<template>
  <a-modal v-model:open="visible" title="应用详情" :footer="null" width="500px">
    <div class="app-detail-content">
      <!-- 应用基础信息 -->
      <div class="app-basic-info">
        <div class="info-item">
          <span class="info-label">创建者：</span>
          <UserInfo :user="app?.user" size="small" />
        </div>
        <div class="info-item">
          <span class="info-label">创建时间：</span>
          <span>{{ formatTime(app?.createTime) }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">生成类型：</span>
          <a-tag v-if="app?.codeGenType" color="blue">
            {{ formatCodeGenType(app.codeGenType) }}
          </a-tag>
          <span v-else>未知类型</span>
        </div>
        <div class="info-item">
          <span class="info-label">生成状态：</span>
          <a-tag v-if="app?.genStatus === 'generating'" color="processing">生成中</a-tag>
          <a-tag v-else-if="app?.genStatus === 'succeeded'" color="success">生成成功</a-tag>
          <a-tag v-else-if="app?.genStatus === 'failed'" color="error">生成失败</a-tag>
          <a-tag v-else>未开始</a-tag>
        </div>
        <div class="info-item">
          <span class="info-label">当前版本：</span>
          <span>{{ app?.currentVersion ? `v${app.currentVersion}` : '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">部署状态：</span>
          <a-tag v-if="!app?.deployKey">未部署</a-tag>
          <a-tag v-else-if="app?.deployStatus === 'online'" color="success">已上线</a-tag>
          <a-tag v-else>已下线</a-tag>
        </div>
        <div class="info-item">
          <span class="info-label">可见范围：</span>
          <a-tag v-if="app?.visibility === 'private'" color="orange">私有</a-tag>
          <a-tag v-else color="green">公开</a-tag>
        </div>
        <div v-if="tagList.length" class="info-item">
          <span class="info-label">应用标签：</span>
          <a-tag v-for="tag in tagList" :key="tag" color="blue">{{ tag }}</a-tag>
        </div>
      </div>

      <!-- 操作栏（仅本人或管理员可见） -->
      <div v-if="showActions" class="app-actions">
        <a-space>
          <a-button type="primary" @click="handleEdit">
            <template #icon>
              <EditOutlined />
            </template>
            修改
          </a-button>
          <a-popconfirm
            title="确定要删除这个应用吗？"
            @confirm="handleDelete"
            ok-text="确定"
            cancel-text="取消"
          >
            <a-button danger>
              <template #icon>
                <DeleteOutlined />
              </template>
              删除
            </a-button>
          </a-popconfirm>
        </a-space>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import UserInfo from './UserInfo.vue'
import { formatTime } from '@/utils/time'
import {formatCodeGenType} from "../utils/codeGenTypes.ts";

interface Props {
  open: boolean
  app?: API.AppVO
  showActions?: boolean
}

interface Emits {
  (e: 'update:open', value: boolean): void
  (e: 'edit'): void
  (e: 'delete'): void
}

const props = withDefaults(defineProps<Props>(), {
  showActions: false,
})

const emit = defineEmits<Emits>()

const visible = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value),
})

const tagList = computed(() => {
  if (!props.app?.tags) return []
  return props.app.tags.split(',').filter(Boolean)
})

const handleEdit = () => {
  emit('edit')
}

const handleDelete = () => {
  emit('delete')
}
</script>

<style scoped>
.app-detail-content {
  padding: 8px 0;
}

.app-basic-info {
  margin-bottom: 24px;
}

.info-item {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.info-label {
  width: 90px;
  color: #666;
  font-size: 14px;
  flex-shrink: 0;
}

.app-actions {
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}
</style>
