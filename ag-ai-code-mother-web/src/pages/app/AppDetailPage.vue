<template>
  <div class="detail-page">
    <a-page-header class="page-header" title="应用详情" @back="router.back()">
      <template #subTitle>
        <span>{{ app.appName || '未命名应用' }} · v{{ app.currentVersion }}</span>
      </template>
      <template #extra>
        <a-button type="primary" @click="handleDeploy" :loading="deploying">部署应用</a-button>
      </template>
    </a-page-header>

    <a-spin :spinning="loading">
      <template v-if="app.id">
        <a-tabs v-model:activeKey="activeTab">
          <a-tab-pane key="chat" tab="AI 对话生成">
            <div class="chat-panel">
              <div class="chat-messages" ref="msgRef">
                <a-empty
                  v-if="messages.length === 0"
                  description="输入你的需求，AI 将为你生成应用代码"
                />
                <div v-for="(msg, index) in messages" :key="index" class="message">
                  <div :class="['bubble', msg.role]">
                    <MarkdownRenderer v-if="msg.role === 'ai' && msg.content" :content="msg.content" />
                    <span v-else>{{ msg.content }}</span>
                  </div>
                </div>
                <a-spin v-if="generating" size="small" class="typing" />
              </div>

              <div class="chat-input">
                <a-textarea
                  v-model:value="input"
                  :rows="3"
                  placeholder="描述你想让 AI 生成的应用，例如：做一个番茄钟应用"
                  :disabled="generating"
                  @keydown.enter.exact.prevent="handleSend"
                />
                <div class="chat-actions">
                  <a-button v-if="generating" danger @click="stopGenerate">停止生成</a-button>
                  <a-button type="primary" :loading="generating" @click="handleSend">
                    发送
                  </a-button>
                </div>
              </div>
            </div>
          </a-tab-pane>

          <a-tab-pane key="preview" tab="预览">
            <div class="preview-toolbar">
              <a-button size="small" @click="refreshState">刷新预览</a-button>
            </div>
            <iframe
              v-if="previewUrl"
              class="preview-frame"
              :src="previewUrl"
              title="应用预览"
            ></iframe>
          </a-tab-pane>

          <a-tab-pane key="versions" tab="版本管理">
            <a-card size="small" class="diff-card" title="版本对比">
              <div class="diff-toolbar">
                <span>基准版本</span>
                <a-select
                  v-model:value="baseVersion"
                  style="width: 130px"
                  :options="versionOptions"
                />
                <span>对比版本</span>
                <a-select
                  v-model:value="targetVersion"
                  style="width: 130px"
                  :options="versionOptions"
                />
                <span>文件</span>
                <a-select v-model:value="diffFile" style="width: 140px" :options="fileOptions" />
                <a-button type="primary" @click="loadDiff" :loading="diffLoading">生成对比</a-button>
              </div>
              <a-alert
                v-if="diffError"
                type="error"
                class="diff-err"
                :message="diffError"
                show-icon
              />
              <div v-if="oldText !== '' && newText !== ''" class="diff-view">
                <CodeDiff :old-text="oldText" :new-text="newText" />
              </div>
            </a-card>

            <a-table
              :loading="versionLoading"
              :data-source="versions"
              :columns="versionColumns"
              row-key="version"
              :pagination="false"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'isCurrent'">
                  <a-tag v-if="record.isCurrent" color="success">当前生效</a-tag>
                  <a-button
                    v-else
                    type="link"
                    @click="handleRollback(record.version)"
                  >
                    回退到此版本
                  </a-button>
                </template>
              </template>
            </a-table>
          </a-tab-pane>
        </a-tabs>
      </template>
      <a-empty v-else description="应用不存在或无权访问" />
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  deployApp,
  getAppVo,
  listAppVersions,
  rollbackApp,
  type AppVO,
  type AppVersionVO,
} from '@/api/app'
import { getStaticPreviewUrl } from '@/config/env'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import CodeDiff from '@/components/CodeDiff.vue'

const route = useRoute()
const router = useRouter()
const appId = computed(() => Number(route.params.id))

const loading = ref(false)
const deploying = ref(false)
const app = ref<AppVO>({})
const activeTab = ref('chat')

// 聊天
const messages = ref<{ role: 'user' | 'ai'; content: string }[]>([])
const input = ref('')
const generating = ref(false)
const msgRef = ref<HTMLElement>()
let eventSource: EventSource | null = null

// 版本
const versions = ref<AppVersionVO[]>([])
const versionLoading = ref(false)
const baseVersion = ref<number>()
const targetVersion = ref<number>()
const diffFile = ref('index.html')
const diffLoading = ref(false)
const diffError = ref('')
const oldText = ref('')
const newText = ref('')
let diffInitialized = false

const versionColumns = [
  { title: '版本号', dataIndex: 'version', key: 'version', width: 120 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '状态', key: 'isCurrent' },
]

const versionOptions = computed(() =>
  versions.value.map((v) => ({
    label: `v${v.version}${v.isCurrent ? '（当前）' : ''}`,
    value: v.version,
  })),
)

const fileOptions = [
  { label: 'index.html', value: 'index.html' },
  { label: 'style.css', value: 'style.css' },
  { label: 'script.js', value: 'script.js' },
]

const previewUrl = computed(() =>
  app.value.codeGenType && app.value.id
    ? getStaticPreviewUrl(app.value.codeGenType, app.value.id)
    : '',
)

const loadApp = async () => {
  loading.value = true
  try {
    app.value = await getAppVo(appId.value)
  } catch {
    // 提示已由拦截器处理
  } finally {
    loading.value = false
  }
}

const loadVersions = async () => {
  versionLoading.value = true
  try {
    versions.value = await listAppVersions(appId.value)
    if (!diffInitialized && versions.value.length > 0) {
      const current = versions.value.find((v) => v.isCurrent)
      baseVersion.value = current?.version ?? versions.value[versions.value.length - 1].version
      targetVersion.value = versions.value[0].version
      diffInitialized = true
    }
  } catch {
    // 提示已由拦截器处理
  } finally {
    versionLoading.value = false
  }
}

const refreshState = async () => {
  await Promise.all([loadApp(), loadVersions()])
}

const scrollToBottom = async () => {
  await nextTick()
  if (msgRef.value) {
    msgRef.value.scrollTop = msgRef.value.scrollHeight
  }
}

const handleSend = async () => {
  const text = input.value.trim()
  if (!text || generating.value) return
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'ai', content: '' })
  input.value = ''
  generating.value = true
  await scrollToBottom()

  const aiMsg = messages.value[messages.value.length - 1]
  eventSource = new EventSource(
    `/api/app/chat/gen/code?appId=${appId.value}&message=${encodeURIComponent(text)}`,
  )

  eventSource.onmessage = (e) => {
    try {
      const data = JSON.parse(e.data) as { d?: string }
      aiMsg.content += data.d || ''
      scrollToBottom()
    } catch {
      // 忽略无法解析的分片
    }
  }

  eventSource.addEventListener('done', () => {
    eventSource?.close()
    eventSource = null
    generating.value = false
    message.success('代码生成完成')
    refreshState()
  })

  eventSource.onerror = () => {
    eventSource?.close()
    eventSource = null
    generating.value = false
    if (!aiMsg.content) {
      messages.value.pop()
    }
  }
}

const stopGenerate = () => {
  eventSource?.close()
  eventSource = null
  generating.value = false
}

const handleDeploy = async () => {
  deploying.value = true
  try {
    const url = await deployApp(appId.value)
    message.success('部署成功')
    window.open(url, '_blank')
  } catch {
    // 提示已由拦截器处理
  } finally {
    deploying.value = false
  }
}

const handleRollback = async (version?: number) => {
  if (!version) return
  await rollbackApp(appId.value, version)
  message.success(`已回退到 v${version}`)
  refreshState()
}

const fetchVersionFile = async (version: number, file: string) => {
  const path = `/api/static/${app.value.codeGenType}_${app.value.id}/v${version}/${file}`
  const res = await fetch(path)
  if (!res.ok) {
    throw new Error(`读取 ${file} 失败（HTTP ${res.status}）`)
  }
  return res.text()
}

const loadDiff = async () => {
  if (!baseVersion.value || !targetVersion.value) {
    message.warning('请选择基准版本和对比版本')
    return
  }
  if (baseVersion.value === targetVersion.value) {
    message.warning('基准版本与对比版本不能相同')
    return
  }
  if (!app.value.codeGenType || !app.value.id) {
    message.warning('应用信息未加载')
    return
  }
  diffLoading.value = true
  diffError.value = ''
  try {
    const base = await fetchVersionFile(baseVersion.value, diffFile.value)
    const target = await fetchVersionFile(targetVersion.value, diffFile.value)
    oldText.value = base
    newText.value = target
  } catch (e) {
    diffError.value = (e as Error).message || '无法读取版本文件'
    oldText.value = ''
    newText.value = ''
  } finally {
    diffLoading.value = false
  }
}

onMounted(() => {
  loadApp()
  loadVersions()
})

onUnmounted(() => {
  eventSource?.close()
})
</script>

<style scoped>
.detail-page {
  background: #fff;
  border-radius: 12px;
  padding: 8px 16px 16px;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 300px);
  min-height: 460px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}

.message {
  display: flex;
  margin-bottom: 12px;
}

.message:has(.bubble.user) {
  justify-content: flex-end;
}

.bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 10px;
  line-height: 1.6;
  font-size: 14px;
}

.bubble.user {
  background: #1677ff;
  color: #fff;
}

.bubble.ai {
  background: #fff;
  border: 1px solid #eee;
}

.typing {
  margin-left: 12px;
}

.chat-input {
  margin-top: 12px;
}

.chat-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
  gap: 8px;
}

.preview-toolbar {
  margin-bottom: 12px;
}

.preview-frame {
  width: 100%;
  height: calc(100vh - 260px);
  min-height: 500px;
  border: 1px solid #eee;
  border-radius: 8px;
}

.diff-card {
  margin-bottom: 16px;
}

.diff-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.diff-toolbar span {
  color: #666;
  font-size: 13px;
}

.diff-err {
  margin-bottom: 12px;
}

.diff-view {
  margin-top: 12px;
  overflow-x: auto;
  max-height: 520px;
  overflow-y: auto;
}
</style>
