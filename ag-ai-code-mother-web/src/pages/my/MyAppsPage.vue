<template>
  <div>
    <div class="page-head">
      <div>
        <h1>我的应用</h1>
        <p>管理你创建的全部 AI 生成应用。</p>
      </div>
      <a-button type="primary" @click="openCreate">创建应用</a-button>
    </div>

    <a-spin :spinning="loading">
      <a-empty v-if="!loading && apps.length === 0" description="还没有应用，点击右上角创建" />
      <a-row v-else :gutter="[16, 16]">
        <a-col v-for="app in apps" :key="app.id" :xs="24" :sm="12" :md="8">
          <a-card hoverable class="app-card">
            <div class="app-name">{{ app.appName || '未命名应用' }}</div>
            <div class="app-desc">{{ app.initPrompt || '暂无描述' }}</div>
            <div class="app-meta">
              <span>v{{ app.currentVersion }}</span>
              <span> · {{ (app.createTime || '').slice(0, 10) }}</span>
            </div>
            <div class="app-actions">
              <a-button type="primary" size="small" @click="goDetail(app)">进入</a-button>
              <a-popconfirm title="确定删除该应用吗？" @confirm="handleDelete(app)">
                <a-button size="small" danger>删除</a-button>
              </a-popconfirm>
            </div>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>

    <div v-if="total > pageSize" class="pagination">
      <a-pagination
        v-model:current="pageNum"
        :page-size="pageSize"
        :total="total"
        @change="loadApps"
      />
    </div>

    <a-modal v-model:open="createOpen" title="创建应用" @ok="handleCreate" :confirm-loading="creating">
      <a-form :model="createForm" layout="vertical">
        <a-form-item label="初始提示词 (Init Prompt)" help="AI 将根据此提示词生成应用的初始形态，应用名称将取前 12 位。">
          <a-textarea
            v-model:value="createForm.initPrompt"
            :rows="4"
            placeholder="例如：帮我生成一个番茄钟应用，包含计时、暂停、重置功能"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { addApp, deleteApp, listMyAppByPage, type AppVO } from '@/api/app'

const router = useRouter()
const loading = ref(false)
const creating = ref(false)
const apps = ref<AppVO[]>([])
const pageNum = ref(1)
const pageSize = 9
const total = ref(0)

const createOpen = ref(false)
const createForm = reactive({ initPrompt: '' })

const loadApps = async () => {
  loading.value = true
  try {
    const res = await listMyAppByPage({ pageNum: pageNum.value, pageSize })
    apps.value = res.records || []
    total.value = res.totalRow || 0
  } catch {
    // 提示已由拦截器处理
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  createForm.initPrompt = ''
  createOpen.value = true
}

const handleCreate = async () => {
  if (!createForm.initPrompt?.trim()) {
    message.warning('请输入初始提示词')
    return
  }
  creating.value = true
  try {
    const id = await addApp({ initPrompt: createForm.initPrompt.trim() })
    message.success('创建成功，即将进入 AI 对话')
    createOpen.value = false
    router.push(`/app/${id}`)
  } catch {
    // 提示已由拦截器处理
  } finally {
    creating.value = false
  }
}

const goDetail = (app: AppVO) => {
  router.push(`/app/${app.id}`)
}

const handleDelete = async (app: AppVO) => {
  if (!app.id) return
  await deleteApp(app.id)
  message.success('删除成功')
  loadApps()
}

onMounted(loadApps)
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-head p {
  color: #888;
}

.app-card {
  border-radius: 10px;
  min-height: 170px;
}

.app-name {
  font-size: 16px;
  font-weight: 600;
}

.app-desc {
  color: #666;
  font-size: 13px;
  margin-top: 6px;
  min-height: 40px;
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

.app-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.pagination {
  margin-top: 24px;
  text-align: center;
}
</style>
