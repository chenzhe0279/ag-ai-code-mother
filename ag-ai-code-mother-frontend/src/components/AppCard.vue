<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { App } from 'ant-design-vue'
import {
  AppstoreOutlined,
  ArrowRightOutlined,
  CodeOutlined,
  DeleteOutlined,
  ExportOutlined,
  LayoutOutlined,
  PushpinFilled,
  PushpinOutlined,
} from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { formatCodeGenType } from '@/utils/codeGenTypes'

const props = defineProps<{ app: API.AppVO }>()
const emit = defineEmits<{
  'view-chat': [appId: API.AppVO['id']]
  'view-work': [app: API.AppVO]
  'pin-toggle': [app: API.AppVO]
  'delete-app': [app: API.AppVO]
}>()

const { modal } = App.useApp()
const loginUserStore = useLoginUserStore()
const coverFailed = ref(false)
watch(
  () => props.app.cover,
  () => {
    coverFailed.value = false
  },
)

const title = computed(() => props.app.appName || '未命名应用')
const isPinned = computed(() => props.app.priority === 999)
const canManage = computed(() => {
  const user = loginUserStore.loginUser
  return !!user.id && (String(props.app.userId) === String(user.id) || user.userRole === 'admin')
})
const canOpenWork = computed(() => !!props.app.deployKey && props.app.deployStatus !== 'offline')
const tags = computed(
  () =>
    props.app.tags
      ?.split(',')
      .map((tag) => tag.trim())
      .filter(Boolean)
      .slice(0, 3) ?? [],
)

const coverIcons = { html: LayoutOutlined, multi_file: AppstoreOutlined, vue_project: CodeOutlined }
const coverIcon = computed(
  () => coverIcons[props.app.codeGenType as keyof typeof coverIcons] ?? CodeOutlined,
)

function confirmDelete() {
  modal.confirm({
    title: '删除作品',
    content: '确定删除「' + title.value + '」吗？删除后无法恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => emit('delete-app', props.app),
  })
}
</script>

<template>
  <article class="app-card">
    <button
      class="app-card-main"
      type="button"
      :aria-label="'查看对话：' + title"
      @click="emit('view-chat', app.id)"
    >
      <span class="card-media">
        <img
          v-if="app.cover && !coverFailed"
          :src="app.cover"
          alt=""
          loading="lazy"
          @error="coverFailed = true"
        />
        <span v-else class="card-fallback" aria-hidden="true">
          <span><component :is="coverIcon" /></span>
        </span>
      </span>
      <span class="card-copy">
        <span class="card-title">{{ title }}</span>
        <span v-if="app.initPrompt" class="card-description">{{ app.initPrompt }}</span>
      </span>
    </button>

    <div class="card-footer">
      <div class="card-tags">
        <span v-if="app.codeGenType" class="card-tag">{{
          formatCodeGenType(app.codeGenType)
        }}</span>
        <span v-if="app.visibility === 'private'" class="card-tag">私有</span>
        <span v-if="isPinned" class="card-tag">已置顶</span>
        <span v-if="app.genStatus === 'generating'" class="card-tag card-tag--pending">生成中</span>
        <span v-if="app.genStatus === 'failed'" class="card-tag card-tag--failed">生成失败</span>
        <span
          v-if="app.deployKey && app.deployStatus === 'online'"
          class="card-tag card-tag--online"
          >已上线</span
        >
        <span v-if="app.deployKey && app.deployStatus === 'offline'" class="card-tag">已下线</span>
        <span v-for="(tag, index) in tags" :key="index" class="card-tag">{{ tag }}</span>
      </div>
      <div class="card-actions">
        <button
          v-if="canManage"
          type="button"
          :aria-label="isPinned ? '取消置顶' : '置顶'"
          :title="isPinned ? '取消置顶' : '置顶'"
          @click="emit('pin-toggle', app)"
        >
          <PushpinFilled v-if="isPinned" /><PushpinOutlined v-else />
        </button>
        <button
          v-if="canManage"
          class="delete-action"
          type="button"
          aria-label="删除作品"
          title="删除作品"
          @click="confirmDelete"
        >
          <DeleteOutlined />
        </button>
        <button
          v-if="canOpenWork"
          type="button"
          aria-label="查看上线作品"
          title="查看上线作品"
          @click="emit('view-work', app)"
        >
          <ExportOutlined />
        </button>
        <button
          type="button"
          aria-label="查看对话"
          title="查看对话"
          @click="emit('view-chat', app.id)"
        >
          <ArrowRightOutlined />
        </button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.app-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 6px;
  border: 1px solid var(--glass-edge);
  border-radius: 20px;
  background: linear-gradient(145deg, #fff, #f4f7fa);
  box-shadow: 0 12px 24px -22px #536783b0;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.app-card:hover {
  border-color: #c8d4e5;
  box-shadow: 0 13px 26px -19px #465f838c;
}

.app-card-main {
  display: grid;
  width: 100%;
  min-width: 0;
  gap: 12px;
  padding: 0;
  border: 0;
  border-radius: 15px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: start;
  cursor: pointer;
}

.card-media {
  display: block;
  width: 100%;
  overflow: hidden;
  aspect-ratio: 2.12;
  border-radius: 14px;
  background: #e4eaf3;
}

.card-media img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top;
}
.card-fallback {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  background:
    radial-gradient(ellipse at 26% 14%, #fff, transparent 65%),
    linear-gradient(145deg, #eaf0f6, #cdd8e6);
}
.card-fallback > span {
  display: grid;
  width: 49px;
  height: 49px;
  place-items: center;
  border: 1px solid #fff;
  border-radius: 15px;
  background: #ffffff9c;
  color: #4e6079;
  font-size: 22px;
  box-shadow: 0 12px 20px -14px #49607e90;
}

.card-copy {
  display: grid;
  min-width: 0;
  gap: 4px;
  padding-inline: 9px;
}
.card-title {
  color: var(--text);
  font-weight: 700;
  font-size: 15px;
  overflow-wrap: anywhere;
}
.card-description {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-dim);
  font-size: 13px;
  line-height: 1.55;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.card-footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-direction: column;
  gap: 8px;
  margin-top: auto;
  padding: 10px 9px 4px;
}

.card-tags,
.card-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
}
.card-actions {
  align-self: flex-end;
}
.card-tags {
  min-width: 0;
}
.card-tag {
  max-width: 100%;
  padding: 3px 7px;
  border-radius: 999px;
  background: #edf0f5;
  color: var(--text-dim);
  font-size: 11px;
  line-height: 1.25;
  overflow-wrap: anywhere;
}
.card-tag--online {
  background: #e5f3ec;
  color: var(--success);
}
.card-tag--failed {
  background: #fcebee;
  color: var(--danger);
}
.card-tag--pending {
  background: #f9f0db;
  color: var(--warning);
}

.card-actions button {
  display: grid;
  width: 33px;
  height: 33px;
  place-items: center;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: #fff;
  color: var(--text-dim);
  cursor: pointer;
  transition:
    background 180ms ease,
    color 180ms ease;
}
.card-actions button:hover {
  color: var(--accent);
  background: var(--accent-soft);
}
.card-actions .delete-action:hover {
  color: var(--danger);
  background: #fcebee;
}

@media (pointer: coarse) {
  .card-actions button {
    width: 42px;
    height: 42px;
  }
}
</style>
