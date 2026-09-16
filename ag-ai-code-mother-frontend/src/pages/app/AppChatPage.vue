<template>
  <div id="appChatPage">
    <!-- 顶部栏 -->
    <div class="header-bar">
      <div class="header-left">
        <h1 class="app-name">{{ appInfo?.appName || '网站生成器' }}</h1>
        <a-tag v-if="appInfo?.codeGenType" color="blue" class="code-gen-type-tag">
          {{ formatCodeGenType(appInfo.codeGenType) }}
        </a-tag>
        <a-tag v-if="appInfo?.currentVersion" color="geekblue" class="code-gen-type-tag">
          v{{ appInfo.currentVersion }}
        </a-tag>
        <a-tag
          v-if="appInfo?.deployKey && appInfo?.deployStatus === 'online'"
          color="success"
          class="code-gen-type-tag"
        >
          已上线
        </a-tag>
        <a-tag
          v-if="appInfo?.deployKey && appInfo?.deployStatus === 'offline'"
          class="code-gen-type-tag"
        >
          已下线
        </a-tag>
      </div>
      <div class="header-right">
        <a-button type="default" @click="showAppDetail">
          <template #icon>
            <InfoCircleOutlined />
          </template>
          应用详情
        </a-button>
        <a-button v-if="isOwner || isAdmin" type="default" @click="openVersionDrawer">
          <template #icon>
            <HistoryOutlined />
          </template>
          历史版本
        </a-button>
        <a-button
          type="primary"
          ghost
          @click="downloadCode()"
          :loading="downloading"
          :disabled="!isOwner"
        >
          <template #icon>
            <DownloadOutlined />
          </template>
          下载代码
        </a-button>
        <a-button type="primary" @click="deployApp" :loading="deploying">
          <template #icon>
            <CloudUploadOutlined />
          </template>
          部署
        </a-button>
        <a-button
          v-if="appInfo?.deployKey && appInfo?.deployStatus === 'online'"
          danger
          ghost
          :loading="undeploying"
          @click="undeployCurrentApp"
        >
          <template #icon>
            <CloudDownloadOutlined />
          </template>
          下线
        </a-button>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content">
      <!-- 左侧对话区域 -->
      <div class="chat-section">
        <!-- 消息区域 -->
        <div class="messages-container" ref="messagesContainer">
          <!-- 加载更多按钮 -->
          <div v-if="hasMoreHistory" class="load-more-container">
            <a-button type="link" @click="loadMoreHistory" :loading="loadingHistory" size="small">
              加载更多历史消息
            </a-button>
          </div>
          <div v-for="(message, index) in messages" :key="index" class="message-item">
            <div v-if="message.type === 'user'" class="user-message">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-avatar">
                <a-avatar :src="resolveAvatarUrl(loginUserStore.loginUser.userAvatar)" />
              </div>
            </div>
            <div v-else class="ai-message">
              <div class="message-avatar">
                <a-avatar :src="aiAvatar" />
              </div>
              <div class="message-content">
                <MarkdownRenderer v-if="message.content" :content="message.content" />
                <div v-if="message.loading" class="loading-indicator">
                  <span>正在深度思考中…</span>
                  <span class="thinking-dots"><b></b><b></b><b></b></span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 选中元素信息展示 -->
        <a-alert
          v-if="selectedElementInfo"
          class="selected-element-alert"
          type="info"
          closable
          @close="clearSelectedElement"
        >
          <template #message>
            <div class="selected-element-info">
              <div class="element-header">
                <span class="element-tag">
                  选中元素：{{ selectedElementInfo.tagName.toLowerCase() }}
                </span>
                <span v-if="selectedElementInfo.id" class="element-id">
                  #{{ selectedElementInfo.id }}
                </span>
                <span v-if="selectedElementInfo.className" class="element-class">
                  .{{ selectedElementInfo.className.split(' ').join('.') }}
                </span>
              </div>
              <div class="element-details">
                <div v-if="selectedElementInfo.textContent" class="element-item">
                  内容: {{ selectedElementInfo.textContent.substring(0, 50) }}
                  {{ selectedElementInfo.textContent.length > 50 ? '...' : '' }}
                </div>
                <div v-if="selectedElementInfo.pagePath" class="element-item">
                  页面路径: {{ selectedElementInfo.pagePath }}
                </div>
                <div class="element-item">
                  选择器:
                  <code class="element-selector-code">{{ selectedElementInfo.selector }}</code>
                </div>
              </div>
            </div>
          </template>
        </a-alert>

        <!-- 用户消息输入框 -->
        <div class="input-container">
          <div class="input-wrapper">
            <a-tooltip v-if="!isOwner" title="无法在别人的作品下对话哦~" placement="top">
              <a-textarea
                v-model:value="userInput"
                :placeholder="getInputPlaceholder()"
                :rows="4"
                :maxlength="1000"
                @keydown.enter.prevent="sendMessage"
                :disabled="isGenerating || !isOwner"
              />
            </a-tooltip>
            <a-textarea
              v-else
              v-model:value="userInput"
              :placeholder="getInputPlaceholder()"
              :rows="4"
              :maxlength="1000"
              @keydown.enter.prevent="sendMessage"
              :disabled="isGenerating"
            />
            <div class="input-actions">
              <a-tooltip
                v-if="isOwner && previewUrl"
                :title="isEditMode ? '退出可视化编辑模式' : '进入可视化编辑模式'"
                placement="top"
              >
                <a-button
                  class="edit-toggle-btn"
                  :class="{ 'edit-mode-active': isEditMode }"
                  :type="isEditMode ? 'primary' : 'default'"
                  :disabled="isGenerating || !previewReady"
                  @click="toggleEditMode"
                >
                  <template #icon>
                    <EditOutlined />
                  </template>
                </a-button>
              </a-tooltip>
              <a-button
                v-if="isGenerating"
                class="stop-btn"
                title="停止生成"
                @click="stopGeneration"
              >
                ■
              </a-button>
              <a-button v-else type="primary" @click="sendMessage" :disabled="!isOwner">
                <template #icon>
                  <SendOutlined />
                </template>
              </a-button>
            </div>
          </div>
        </div>
      </div>
      <!-- 右侧网页展示区域 -->
      <div class="preview-section">
        <div class="preview-header">
          <h3>生成后的网页展示</h3>
          <div class="preview-actions">
            <a-button v-if="previewUrl" type="link" @click="openInNewTab">
              <template #icon>
                <ExportOutlined />
              </template>
              新窗口打开
            </a-button>
            <a-button
              v-if="previewUrl"
              type="link"
              :loading="previewBuilding"
              @click="updatePreview"
            >
              <template #icon>
                <ReloadOutlined />
              </template>
              刷新预览
            </a-button>
          </div>
        </div>
        <div class="preview-content">
          <div v-if="!previewUrl && !isGenerating && !previewError" class="preview-placeholder">
            <div class="placeholder-icon">🌐</div>
            <p>网站文件生成完成后将在这里展示</p>
          </div>
          <div v-else-if="isGenerating" class="preview-loading">
            <a-spin size="large" />
            <p>正在生成网站...</p>
          </div>
          <div v-else-if="previewBuilding" class="preview-loading">
            <a-spin size="large" />
            <p>Vue 项目构建中，请稍候...</p>
          </div>
          <div v-else-if="previewError" class="preview-loading preview-error">
            <p>{{ previewError }}</p>
            <a-button type="primary" ghost @click="updatePreview">重新加载预览</a-button>
          </div>
          <iframe
            v-else
            ref="previewIframe"
            :key="previewFrameKey"
            :src="previewUrl"
            class="preview-iframe"
            frameborder="0"
            @load="onIframeLoad"
          ></iframe>
        </div>
      </div>
    </div>

    <!-- 应用详情弹窗 -->
    <AppDetailModal
      v-model:open="appDetailVisible"
      :app="appInfo"
      :show-actions="isOwner || isAdmin"
      @edit="editApp"
      @delete="deleteApp"
    />

    <!-- 部署成功弹窗 -->
    <DeploySuccessModal
      v-model:open="deployModalVisible"
      :deploy-url="deployUrl"
      @open-site="openDeployedSite"
    />

    <!-- 历史版本抽屉 -->
    <a-drawer v-model:open="versionDrawerVisible" title="历史版本" width="420">
      <a-spin :spinning="versionLoading">
        <a-empty v-if="!versionList.length" description="暂无历史版本" />
        <div v-for="v in versionList" :key="v.version" class="version-item">
          <div class="version-info">
            <span class="version-no">v{{ v.version }}</span>
            <a-tag v-if="v.isCurrent" color="blue">当前</a-tag>
            <span class="version-time">{{ formatTime(v.createTime) }}</span>
          </div>
          <a-space>
            <a-button type="link" size="small" @click="previewVersion(v)">预览</a-button>
            <a-button type="link" size="small" @click="downloadCode(v.version)">下载</a-button>
            <a-popconfirm v-if="!v.isCurrent" title="确定回退到该版本吗？" @confirm="rollbackTo(v)">
              <a-button type="link" size="small" danger>回退到此版本</a-button>
            </a-popconfirm>
          </a-space>
        </div>
      </a-spin>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  getAppVoById,
  deployApp as deployAppApi,
  deleteApp as deleteAppApi,
  listAppVersions,
  rollbackApp as rollbackAppApi,
  undeployApp as undeployAppApi,
} from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { CodeGenTypeEnum, formatCodeGenType } from '@/utils/codeGenTypes'
import { formatTime } from '@/utils/time'
import request from '@/request'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import aiAvatar from '@/assets/aiAvatar.png'
import { API_BASE_URL, getStaticPreviewUrl, resolveAvatarUrl } from '@/config/env'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'

import {
  CloudUploadOutlined,
  CloudDownloadOutlined,
  SendOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  HistoryOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 应用信息
const appInfo = ref<API.AppVO>()
const appId = ref<string>()

// 对话相关
interface Message {
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
}

const messages = ref<Message[]>([])
const userInput = ref('')
const isGenerating = ref(false)
const messagesContainer = ref<HTMLElement>()

// 对话历史相关
const loadingHistory = ref(false)
const hasMoreHistory = ref(false)
const lastCreateTime = ref<string>()
const historyLoaded = ref(false)

// 预览相关
const previewUrl = ref('')
const previewReady = ref(false)
const previewBuilding = ref(false)
const previewError = ref('')
const previewFrameKey = ref(0)
const previewIframe = ref<HTMLIFrameElement>()

// 部署相关
const deploying = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')
const undeploying = ref(false)

// 版本管理相关
const versionDrawerVisible = ref(false)
const versionLoading = ref(false)
const versionList = ref<API.AppVersionVO[]>([])

// 下载相关
const downloading = ref(false)

// 可视化编辑相关
const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo | null>(null)
const visualEditor = new VisualEditor({
  onElementSelected: (elementInfo: ElementInfo) => {
    selectedElementInfo.value = elementInfo
  },
})

// 权限相关
const isOwner = computed(() => {
  return appInfo.value?.userId === loginUserStore.loginUser.id
})

const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin'
})

// 应用详情相关
const appDetailVisible = ref(false)

// 打开历史版本抽屉
const openVersionDrawer = async () => {
  versionDrawerVisible.value = true
  await loadVersionList()
}

// 加载版本列表
const loadVersionList = async () => {
  if (!appId.value) return
  versionLoading.value = true
  try {
    const res = await listAppVersions({ appId: appId.value as unknown as number })
    if (res.data.code === 0) {
      versionList.value = res.data.data || []
    } else {
      message.error('获取版本列表失败：' + res.data.message)
    }
  } catch (error) {
    console.error('获取版本列表失败：', error)
    message.error('获取版本列表失败')
  } finally {
    versionLoading.value = false
  }
}

// 预览指定版本
const previewVersion = (v: API.AppVersionVO) => {
  if (!appInfo.value?.codeGenType || !appId.value || !v.version) return
  const url = getStaticPreviewUrl(appInfo.value.codeGenType, String(appId.value), v.version)
  window.open(url, '_blank')
}

// 回退到指定版本
const rollbackTo = async (v: API.AppVersionVO) => {
  if (!appId.value || !v.version) return
  try {
    const res = await rollbackAppApi({
      appId: appId.value as unknown as number,
      targetVersion: v.version,
    })
    if (res.data.code === 0) {
      message.success(`已回退到 v${v.version}`)
      await loadVersionList()
      await fetchAppInfo()
      updatePreview()
    } else {
      message.error('回退失败：' + res.data.message)
    }
  } catch (error) {
    console.error('回退失败：', error)
    message.error('回退失败，请重试')
  }
}

// 中断生成：关闭事件源，保留已生成内容，可继续对话接续
const stopGeneration = () => {
  activeEventSource?.close()
  activeEventSource = null
  isGenerating.value = false
  message.info('已停止生成，可继续输入接续内容')
}

// 下线当前应用
const undeployCurrentApp = async () => {
  if (!appId.value) return
  undeploying.value = true
  try {
    const res = await undeployAppApi({ appId: appId.value as unknown as number })
    if (res.data.code === 0) {
      message.success('应用已下线')
      await fetchAppInfo()
    } else {
      message.error('下线失败：' + res.data.message)
    }
  } catch (error) {
    console.error('下线失败：', error)
    message.error('下线失败，请重试')
  } finally {
    undeploying.value = false
  }
}

// 显示应用详情
const showAppDetail = () => {
  appDetailVisible.value = true
}

// 加载对话历史
const loadChatHistory = async (isLoadMore = false) => {
  if (!appId.value || loadingHistory.value) return
  loadingHistory.value = true
  try {
    const params: API.listAppChatHistoryParams = {
      appId: appId.value as unknown as number,
      pageSize: 10,
    }
    // 如果是加载更多，传递最后一条消息的创建时间作为游标
    if (isLoadMore && lastCreateTime.value) {
      params.lastCreateTime = lastCreateTime.value
    }
    const res = await listAppChatHistory(params)
    if (res.data.code === 0 && res.data.data) {
      const chatHistories = res.data.data.records || []
      if (chatHistories.length > 0) {
        // 将对话历史转换为消息格式，并按时间正序排列（老消息在前）
        const historyMessages: Message[] = chatHistories
          .map((chat) => ({
            type: (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai',
            content: chat.message || '',
            createTime: chat.createTime,
          }))
          .reverse() // 反转数组，让老消息在前
        if (isLoadMore) {
          // 加载更多时，将历史消息添加到开头
          messages.value.unshift(...historyMessages)
        } else {
          // 初始加载，直接设置消息列表
          messages.value = historyMessages
        }
        // 更新游标
        lastCreateTime.value = chatHistories[chatHistories.length - 1]?.createTime
        // 检查是否还有更多历史
        hasMoreHistory.value = chatHistories.length === 10
      } else {
        hasMoreHistory.value = false
      }
      historyLoaded.value = true
    }
  } catch (error) {
    console.error('加载对话历史失败：', error)
    message.error('加载对话历史失败')
  } finally {
    loadingHistory.value = false
  }
}

// 加载更多历史消息
const loadMoreHistory = async () => {
  await loadChatHistory(true)
}

// 获取应用信息
const fetchAppInfo = async () => {
  const id = route.params.id as string
  if (!id) {
    message.error('应用ID不存在')
    router.push('/')
    return
  }

  appId.value = id

  try {
    const res = await getAppVoById({ id: id as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data

      // 先加载对话历史
      await loadChatHistory()
      // 已有成功生成的版本时直接加载预览，不依赖对话历史是否完整返回
      if (appInfo.value.currentVersion && appInfo.value.genStatus === 'succeeded') {
        await updatePreview()
      }
      // 检查是否需要自动发送初始提示词
      // 只有在是自己的应用且没有对话历史时才自动发送
      if (
        appInfo.value.initPrompt &&
        isOwner.value &&
        messages.value.length === 0 &&
        historyLoaded.value
      ) {
        await sendInitialMessage(appInfo.value.initPrompt)
      }
    } else {
      message.error('获取应用信息失败')
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败：', error)
    message.error('获取应用信息失败')
    router.push('/')
  }
}

// 发送初始消息
const sendInitialMessage = async (prompt: string) => {
  // 添加用户消息
  messages.value.push({
    type: 'user',
    content: prompt,
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  isGenerating.value = true
  if (appInfo.value) {
    appInfo.value.genStatus = 'generating'
  }
  await generateCode(prompt, aiMessageIndex)
}

// 发送消息
const sendMessage = async () => {
  if (!userInput.value.trim() || isGenerating.value) {
    return
  }

  let message = userInput.value.trim()
  const hasSelectedElement = Boolean(selectedElementInfo.value)
  // 如果有选中的元素，将元素信息添加到提示词中
  if (selectedElementInfo.value) {
    let elementContext = `\n\n选中元素信息：`
    if (selectedElementInfo.value.pagePath) {
      elementContext += `\n- 页面路径: ${selectedElementInfo.value.pagePath}`
    }
    elementContext += `\n- 标签: ${selectedElementInfo.value.tagName.toLowerCase()}\n- 选择器: ${selectedElementInfo.value.selector}`
    if (selectedElementInfo.value.textContent) {
      elementContext += `\n- 当前内容: ${selectedElementInfo.value.textContent.substring(0, 100)}`
    }
    message += elementContext
  }
  userInput.value = ''
  // 添加用户消息（包含元素信息）
  messages.value.push({
    type: 'user',
    content: message,
  })

  // 发送消息后统一清理选中状态并退出编辑模式，避免影响后续生成流程
  if (hasSelectedElement || isEditMode.value) {
    resetVisualEditing()
  }

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  isGenerating.value = true
  if (appInfo.value) {
    appInfo.value.genStatus = 'generating'
  }
  await generateCode(message, aiMessageIndex)
}

// 组件级事件源引用：卸载时统一关闭，避免热更新/路由切换后残留连接继续推送
let activeEventSource: EventSource | null = null

// 生成代码 - 使用 EventSource 处理流式响应
const generateCode = async (userMessage: string, aiMessageIndex: number) => {
  let streamCompleted = false
  previewError.value = ''

  try {
    // 获取 axios 配置的 baseURL
    const baseURL = request.defaults.baseURL || API_BASE_URL

    // 构建URL参数
    const params = new URLSearchParams({
      appId: appId.value || '',
      message: userMessage,
    })

    const url = `${baseURL}/app/chat/gen/code?${params}`

    // 创建 EventSource 连接（若已有旧连接先关闭，防止重复推送）
    activeEventSource?.close()
    activeEventSource = new EventSource(url, {
      withCredentials: true,
    })
    const eventSource = activeEventSource

    let fullContent = ''
    // 流式渲染节流：AI 回复可能包含超长代码，每个分片都全量重渲染 Markdown 并强制滚动
    // 会持续占满主线程导致"页面无响应"，这里把渲染合并到每 150ms 一次
    let renderScheduled = false
    const renderTarget = () => messages.value[aiMessageIndex]
    const flushRender = () => {
      const target = renderTarget()
      if (!target) return
      target.content = fullContent
      target.loading = false
      scrollToBottom()
    }
    const scheduleRender = () => {
      if (renderScheduled) return
      renderScheduled = true
      setTimeout(() => {
        renderScheduled = false
        flushRender()
      }, 150)
    }

    // 看门狗：后端流异常时可能既不发 done 事件、也不主动断开连接，
    // 此时界面会一直停在"正在生成"。超过 20s 没有新数据就主动收尾。
    let watchdogTimer: number | null = null
    const clearWatchdog = () => {
      if (watchdogTimer !== null) {
        window.clearTimeout(watchdogTimer)
        watchdogTimer = null
      }
    }
    // 统一收尾入口：done 事件、连接中断、看门狗超时三种结束场景共用
    const finalizeStream = (closeConnection: boolean) => {
      const alreadyFinalized = streamCompleted
      streamCompleted = true
      if (!alreadyFinalized) {
        clearWatchdog()
        isGenerating.value = false
        flushRender()
        // 延迟刷新应用信息与预览，确保后端已把代码文件写盘
        setTimeout(async () => {
          await fetchAppInfo()
        }, 1000)
      }
      if (closeConnection) {
        eventSource?.close()
        activeEventSource = null
      }
    }
    const resetWatchdog = () => {
      clearWatchdog()
      // 看门狗只收尾界面、不关闭连接，避免误触发后端把它标记为生成失败
      watchdogTimer = window.setTimeout(() => finalizeStream(false), 20000)
    }
    resetWatchdog()

    // 处理接收到的消息
    eventSource.onmessage = function (event) {
      if (streamCompleted) return

      try {
        // 解析JSON包装的数据
        const parsed = JSON.parse(event.data)
        const content = parsed.d

        // 拼接内容
        if (content !== undefined && content !== null) {
          fullContent += content
          scheduleRender()
          resetWatchdog()
        }
      } catch (error) {
        console.error('解析消息失败:', error)
        handleError(error, aiMessageIndex)
      }
    }

    // 处理done事件
    eventSource.addEventListener('done', function () {
      finalizeStream(true)
    })

    // 处理business-error事件（后端限流等错误）
    eventSource.addEventListener('business-error', function (event: MessageEvent) {
      if (streamCompleted) return

      try {
        const errorData = JSON.parse(event.data)
        console.error('SSE业务错误事件:', errorData)

        // 显示具体的错误信息
        const errorMessage = errorData.message || '生成过程中出现错误'
        messages.value[aiMessageIndex].content = `❌ ${errorMessage}`
        messages.value[aiMessageIndex].loading = false
        message.error(errorMessage)

        finalizeStream(true)
      } catch (parseError) {
        console.error('解析错误事件失败:', parseError, '原始数据:', event.data)
        handleError(new Error('服务器返回错误'), aiMessageIndex)
      }
    })

    // 处理连接中断：后端流异常结束（例如内部报错）时不会发送 done 事件，
    // 只能靠连接状态兜底。只要已经收到过内容，就按"本次生成已结束"收尾。
    eventSource.onerror = function () {
      if (streamCompleted) return
      if (fullContent) {
        finalizeStream(true)
        return
      }
      // 一个字符都没收到就断了，按失败提示
      streamCompleted = true
      clearWatchdog()
      eventSource?.close()
      activeEventSource = null
      handleError(new Error('SSE连接错误'), aiMessageIndex)
    }
  } catch (error) {
    console.error('创建 EventSource 失败：', error)
    handleError(error, aiMessageIndex)
  }
}

// 错误处理函数
const handleError = (error: unknown, aiMessageIndex: number) => {
  console.error('生成代码失败：', error)
  messages.value[aiMessageIndex].content = '抱歉，生成过程中出现了错误，请重试。'
  messages.value[aiMessageIndex].loading = false
  message.error('生成失败，请重试')
  isGenerating.value = false
}

// 探测预览地址是否就绪：Vue 项目在流结束后还需由后端异步构建产出 dist，未就绪时访问会 404
const waitForPreviewReady = async (url: string, maxAttempts = 180, intervalMs = 1000) => {
  for (let i = 0; i < maxAttempts; i++) {
    try {
      const res = await fetch(url, {
        method: 'GET',
        credentials: 'include',
        cache: 'no-store',
      })
      if (res.ok) return true
    } catch {
      // 网络瞬时异常，继续重试
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs))
  }
  return false
}

// 更新预览（手动点击「刷新预览」也会走到这里）
const updatePreview = async () => {
  if (!appId.value) return
  const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML
  const newPreviewUrl = getStaticPreviewUrl(
    codeGenType,
    String(appId.value),
    appInfo.value?.currentVersion,
  )
  previewError.value = ''
  // Vue 工程模式要等后端把 dist 构建出来，先轮询探测再挂 iframe，避免直接展示 404 页面
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    previewBuilding.value = true
    const ready = await waitForPreviewReady(newPreviewUrl)
    previewBuilding.value = false
    if (!ready) {
      previewUrl.value = ''
      previewReady.value = false
      previewError.value = 'Vue 项目仍在构建中，请稍后点击“重新加载预览”'
      return
    }
  }
  previewReady.value = false
  previewUrl.value = newPreviewUrl
  // 同一版本刷新时 URL 不变，更新 key 可强制浏览器重新加载 iframe
  previewFrameKey.value += 1
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 下载代码（不传版本时默认下载当前版本）
const downloadCode = async (version?: number) => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  // 后端 download 接口按 version 定位 v{version} 目录，不传会拼成 vnull 导致 404
  const targetVersion = version ?? appInfo.value?.currentVersion
  if (!targetVersion) {
    message.error('应用代码尚未生成，无法下载')
    return
  }
  downloading.value = true
  try {
    const API_BASE_URL = request.defaults.baseURL || ''
    const url = `${API_BASE_URL}/app/download/${appId.value}?version=${targetVersion}`
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status}`)
    }
    // 后端业务异常由全局异常处理器返回 HTTP 200 + JSON（BaseResponse），
    // 所以不能只靠 response.ok 判断，需按 Content-Type 区分 zip 成功体与 JSON 错误体
    const contentType = response.headers.get('Content-Type') || ''
    if (contentType.includes('application/json')) {
      const errorBody = await response.json()
      throw new Error(errorBody?.message || '下载失败')
    }
    // 获取文件名（兼容带引号与不带引号两种 Content-Disposition）
    const contentDisposition = response.headers.get('Content-Disposition')
    const fileName =
      contentDisposition?.match(/filename="?([^";]+)"?/)?.[1] ||
      `app-${appId.value}-v${targetVersion}.zip`
    // 下载文件
    const blob = await response.blob()
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    // 延迟释放 blob URL：立即 revoke 会让部分浏览器来不及开始下载
    setTimeout(() => URL.revokeObjectURL(downloadUrl), 1000)
    message.success('代码下载成功')
  } catch (error) {
    console.error('下载失败：', error)
    message.error(error instanceof Error ? error.message : '下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// 部署应用
const deployApp = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }

  deploying.value = true
  try {
    const res = await deployAppApi({
      appId: appId.value as unknown as number,
    })

    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deployModalVisible.value = true
      message.success('部署成功')
      await fetchAppInfo()
    } else {
      message.error('部署失败：' + res.data.message)
    }
  } catch (error) {
    console.error('部署失败：', error)
    message.error('部署失败，请重试')
  } finally {
    deploying.value = false
  }
}

// 在新窗口打开预览
const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

// 打开部署的网站
const openDeployedSite = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank')
  }
}

// iframe加载完成
const onIframeLoad = () => {
  previewReady.value = true
  if (previewIframe.value) {
    visualEditor.init(previewIframe.value)
    visualEditor.onIframeLoad()
  }
}

// 编辑应用
const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

// 删除应用
const deleteApp = async () => {
  if (!appInfo.value?.id) return

  try {
    const res = await deleteAppApi({ id: appInfo.value.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      appDetailVisible.value = false
      router.push('/')
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}

// 可视化编辑相关函数
const toggleEditMode = () => {
  if (!previewIframe.value || !previewReady.value) {
    message.warning('请等待页面加载完成')
    return
  }

  const newEditMode = visualEditor.toggleEditMode()
  isEditMode.value = newEditMode
  if (!newEditMode) {
    selectedElementInfo.value = null
  }
}

const clearSelectedElement = () => {
  selectedElementInfo.value = null
  visualEditor.clearSelection()
}

const resetVisualEditing = () => {
  selectedElementInfo.value = null
  isEditMode.value = false
  visualEditor.disableEditMode()
}

const getInputPlaceholder = () => {
  if (selectedElementInfo.value) {
    return `正在编辑 ${selectedElementInfo.value.tagName.toLowerCase()} 元素，描述您想要的修改...`
  }
  return '请描述你想生成的网站，越详细效果越好哦'
}

const handleVisualEditorMessage = (event: MessageEvent) => {
  visualEditor.handleIframeMessage(event)
}

// 页面加载时获取应用信息
onMounted(() => {
  fetchAppInfo()

  // 监听 iframe 消息
  window.addEventListener('message', handleVisualEditorMessage)
})

// 清理资源
onUnmounted(() => {
  // EventSource 不会随组件卸载自动关闭，必须显式 close
  activeEventSource?.close()
  activeEventSource = null
  window.removeEventListener('message', handleVisualEditorMessage)
  visualEditor.destroy()
})
</script>

<style scoped>
#appChatPage {
  /* 固定视口高度（扣除顶部导航与页脚），消息增多时内部滚动，页面不被撑长 */
  height: calc(100vh - 115px);
  display: flex;
  flex-direction: column;
  padding: 16px 24px;
}

@supports (height: 100dvh) {
  #appChatPage {
    height: calc(100dvh - 115px);
  }
}

/* 顶部栏 */
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 18px;
  border: 1px solid rgba(157, 173, 222, 0.14);
  border-radius: 16px;
  background: rgba(16, 25, 50, 0.42);
  backdrop-filter: blur(10px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-gen-type-tag {
  font-size: 12px;
}

.version-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 8px;
  border-bottom: 1px solid rgba(157, 173, 222, 0.14);
}

.version-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.version-no {
  font-weight: 600;
  color: var(--text);
}

.version-time {
  font-size: 12px;
  color: var(--text-faint);
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text);
}

.header-right {
  display: flex;
  gap: 12px;
}

/* 主要内容区域 */
.main-content {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
  padding: 8px;
  overflow: hidden;
}

/* 左侧对话区域 */
.chat-section {
  flex: 2;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(157, 173, 222, 0.14);
  border-radius: 16px;
  background: rgba(13, 20, 42, 0.42);
  backdrop-filter: blur(10px);
  overflow: hidden;
}

.messages-container {
  flex: 0.9;
  min-height: 0;
  padding: 16px;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.message-item {
  margin-bottom: 12px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 8px;
}

.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 8px;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border: 1px solid rgba(138, 166, 232, 0.26);
  border-radius: 5px 17px 17px 17px;
  background: rgba(20, 30, 58, 0.85);
  backdrop-filter: blur(8px);
  color: #f0f4ff;
  font-size: 14px;
  line-height: 1.9;
  word-wrap: break-word;
}

.user-message .message-content {
  border-color: rgba(196, 156, 255, 0.38);
  border-radius: 17px 5px 17px 17px;
  background: rgba(58, 44, 110, 0.85);
  color: #ffffff;
}

.ai-message .message-content {
  padding: 10px 14px;
}

.ai-message .message-content :deep(code) {
  color: #c3a6ff;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-dim);
}

.thinking-dots {
  display: inline-flex;
  gap: 4px;
}

.thinking-dots b {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #b9c8ff;
  box-shadow: 0 0 8px rgba(142, 166, 255, 0.55);
  animation: think-blink 1.2s infinite ease-in-out;
}

.thinking-dots b:nth-child(2) {
  animation-delay: 0.2s;
}

.thinking-dots b:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes think-blink {
  0%,
  80%,
  100% {
    opacity: 0.25;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.stop-btn {
  width: 38px;
  height: 38px;
  padding: 0;
  font-size: 14px;
}

.messages-container {
  /* 隐藏滚动条：仅保留滚轮 / 触控板滚动 */
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.messages-container::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

.message-avatar {
  flex-shrink: 0;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-dim);
}

/* 加载更多按钮 */
.load-more-container {
  text-align: center;
  padding: 8px 0;
  margin-bottom: 16px;
}

/* 输入区域 */
.input-container {
  padding: 12px 16px 16px;
  background: transparent;
}

.input-wrapper {
  position: relative;
}

.input-wrapper :deep(.ant-input) {
  padding-right: 96px;
  background: rgba(17, 26, 49, 0.42);
  border: 1px solid rgba(175, 190, 225, 0.28);
  border-radius: 14px;
  color: #f2f5ff;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.input-wrapper :deep(.ant-input::-webkit-scrollbar) {
  display: none;
  width: 0;
  height: 0;
}

.input-wrapper :deep(.ant-input::placeholder) {
  color: #7987a7;
}

.input-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.edit-toggle-btn {
  width: 38px;
  height: 38px;
  padding: 0;
}

.edit-toggle-btn.edit-mode-active {
  border-color: #52c41a !important;
  background: #52c41a !important;
  color: #fff !important;
  box-shadow: 0 0 0 3px rgba(82, 196, 26, 0.14);
}

.edit-toggle-btn.edit-mode-active:hover {
  border-color: #73d13d !important;
  background: #73d13d !important;
}

/* 右侧预览区域 */
.preview-section {
  flex: 3;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(157, 173, 222, 0.14);
  border-radius: 16px;
  background: rgba(13, 20, 42, 0.42);
  backdrop-filter: blur(10px);
  overflow: hidden;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid rgba(157, 173, 222, 0.14);
}

.preview-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.preview-actions {
  display: flex;
  gap: 8px;
}

.preview-content {
  flex: 1;
  min-height: 0;
  position: relative;
  overflow: hidden;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-faint);
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-faint);
}

.preview-loading p {
  margin-top: 16px;
}

.preview-error {
  gap: 4px;
  padding: 24px;
  text-align: center;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.selected-element-alert {
  margin: 0 16px;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .main-content {
    flex-direction: column;
  }

  .chat-section,
  .preview-section {
    flex: none;
    height: 50vh;
  }
}

@media (max-width: 768px) {
  .header-bar {
    padding: 12px 16px;
  }

  .app-name {
    font-size: 16px;
  }

  .main-content {
    padding: 8px;
    gap: 8px;
  }

  .message-content {
    max-width: 85%;
  }

  /* 选中元素信息样式 */
  .selected-element-alert {
    margin: 0 16px;
  }

  .selected-element-info {
    line-height: 1.4;
  }

  .element-header {
    margin-bottom: 8px;
  }

  .element-details {
    margin-top: 8px;
  }

  .element-item {
    margin-bottom: 4px;
    font-size: 13px;
  }

  .element-item:last-child {
    margin-bottom: 0;
  }

  .element-tag {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 14px;
    font-weight: 600;
    color: #8fd8ff;
  }

  .element-id {
    color: #28a745;
    margin-left: 4px;
  }

  .element-class {
    color: #ffc107;
    margin-left: 4px;
  }

  .element-selector-code {
    font-family: 'Monaco', 'Menlo', monospace;
    background: rgba(9, 17, 39, 0.5);
    padding: 2px 4px;
    border-radius: 3px;
    font-size: 12px;
    color: #ffb0bc;
    border: 1px solid rgba(168, 186, 235, 0.24);
  }
}
</style>
