<template>
  <div
    class="app-card reveal"
    :class="[colorClass, { 'app-card--featured': featured }]"
    role="button"
    tabindex="0"
    :title="app.appName"
    @click="handleViewChat"
    @keydown.enter="handleViewChat"
  >
    <span class="card-slice" aria-hidden="true"></span>
    <span class="card-orbit" aria-hidden="true"></span>
    <span class="card-icon">{{ cardIcon }}</span>
    <span class="card-content">
      <em>{{ cardTag }}</em>
      <strong>
        {{ app.appName || '未命名应用' }}
        <i v-if="app.visibility === 'private'" class="card-flag private">私有</i>
        <i v-if="isPinned" class="card-flag pinned">置顶</i>
      </strong>
      <small class="card-desc">{{ cardDesc }}</small>
      <small v-if="hasStatusTags" class="card-meta">
        <i v-if="app.genStatus === 'generating'" class="flag generating">生成中</i>
        <i v-if="app.genStatus === 'failed'" class="flag failed">生成失败</i>
        <i v-if="app.deployKey && app.deployStatus === 'online'" class="flag online">已上线</i>
        <i v-if="app.deployKey && app.deployStatus === 'offline'" class="flag offline">已下线</i>
        <i v-for="tag in tagList" :key="tag" class="flag tag">{{ tag }}</i>
      </small>
    </span>
    <span class="card-actions">
      <span
        v-if="canManage"
        class="card-action-btn pin"
        role="button"
        :title="isPinned ? '取消置顶' : '置顶'"
        @click.stop="handlePinToggle"
        @keydown.enter.stop="handlePinToggle"
      >✦</span>
      <span
        v-if="canOpenWork"
        class="card-arrow"
        role="button"
        title="查看作品"
        @click.stop="handleViewWork"
        @keydown.enter.stop="handleViewWork"
      >↗</span>
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { formatCodeGenType } from '@/utils/codeGenTypes'

interface Props {
  app: API.AppVO
  featured?: boolean
}

interface Emits {
  (e: 'view-chat', appId: string | number | undefined): void
  (e: 'view-work', app: API.AppVO): void
  (e: 'pin-toggle', app: API.AppVO): void
}

const props = withDefaults(defineProps<Props>(), {
  featured: false,
})

const emit = defineEmits<Emits>()

const loginUserStore = useLoginUserStore()

const isPinned = computed(() => props.app.priority === 999)

const canManage = computed(() => {
  if (!loginUserStore.loginUser.id) return false
  return (
    props.app.userId === loginUserStore.loginUser.id ||
    loginUserStore.loginUser.userRole === 'admin'
  )
})

const canOpenWork = computed(() => {
  return Boolean(props.app.deployKey && props.app.deployStatus !== 'offline')
})

const cardIcon = computed(() => {
  const type = props.app.codeGenType
  if (type === 'html') return '◐'
  if (type === 'multi_file') return '◈'
  if (type === 'vue_project') return '✦'
  return (props.app.appName || 'A').charAt(0)
})

const cardTag = computed(() => {
  if (props.app.codeGenType) {
    return formatCodeGenType(props.app.codeGenType).toUpperCase()
  }
  return 'AI APP'
})

const cardDesc = computed(() => {
  const prompt = props.app.initPrompt || props.app.appName || '一句话生成的应用'
  return prompt.length > 46 ? `${prompt.slice(0, 46)}…` : prompt
})

const tagList = computed(() => {
  if (!props.app.tags) return []
  return props.app.tags.split(',').filter(Boolean).slice(0, 3)
})

const hasStatusTags = computed(() => {
  const app = props.app
  const hasFlag =
    app.genStatus === 'generating' ||
    app.genStatus === 'failed' ||
    Boolean(app.deployKey && (app.deployStatus === 'online' || app.deployStatus === 'offline'))
  return hasFlag || tagList.value.length > 0
})

// 三色系按应用 id 稳定分配，保证同色系分布
const colorClass = computed(() => {
  const id = Number(props.app.id ?? 0)
  return ['rose', 'violet', 'sky'][Math.abs(id) % 3]
})

const handleViewChat = () => {
  emit('view-chat', props.app.id)
}

const handleViewWork = () => {
  emit('view-work', props.app)
}

const handlePinToggle = () => {
  emit('pin-toggle', props.app)
}
</script>

<style scoped>
.app-card {
  position: relative;
  isolation: isolate;
  min-height: 186px;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 26px;
  color: #f5f7ff;
  text-align: left;
  border: 1px solid rgba(168, 186, 235, 0.1);
  border-radius: 22px;
  background: linear-gradient(125deg, rgba(27, 40, 86, 0.16), rgba(16, 25, 54, 0.24));
  box-shadow: inset 0 1px rgba(255, 255, 255, 0.04), 0 22px 55px rgba(2, 5, 17, 0.1);
  backdrop-filter: blur(10px);
  transition: 0.3s ease;
  cursor: pointer;
}
.app-card::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  border-radius: inherit;
  padding: 1px;
  background: linear-gradient(135deg, rgba(178, 190, 255, 0.5), transparent 34%, transparent 66%, rgba(126, 230, 207, 0.34));
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  opacity: 0.35;
  transition: opacity 0.3s;
}
.app-card::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background: linear-gradient(120deg, transparent 25%, rgba(255, 255, 255, 0.09) 47%, transparent 70%);
  transform: translateX(-115%);
  transition: 0.6s;
}
.app-card:hover {
  transform: translateY(-8px);
  box-shadow: inset 0 1px rgba(255, 255, 255, 0.12), 0 28px 70px rgba(2, 5, 17, 0.35);
}
.app-card:hover::before {
  opacity: 1;
}
.app-card:hover::after {
  transform: translateX(115%);
}
.card-slice {
  position: absolute;
  right: -70px;
  top: -70px;
  width: 180px;
  height: 180px;
  border: 1px solid rgba(178, 190, 255, 0.1);
  transform: rotate(24deg);
  pointer-events: none;
}
.card-slice::after {
  content: '';
  position: absolute;
  inset: 18px;
  border: 1px solid rgba(126, 230, 207, 0.09);
}
.card-orbit {
  position: absolute;
  width: 150px;
  height: 150px;
  border: 1px solid rgba(174, 191, 255, 0.24);
  border-radius: 50%;
  right: -44px;
  top: -64px;
  animation: orbit-spin 26s linear infinite;
}
@keyframes orbit-spin {
  to { transform: rotate(360deg); }
}
.card-icon {
  flex: 0 0 54px;
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border: 1px solid rgba(218, 229, 255, 0.3);
  border-radius: 17px;
  background: rgba(203, 216, 255, 0.05);
  color: #dfe7ff;
  font: 26px 'Playfair Display', serif;
  box-shadow: inset 0 1px rgba(255, 255, 255, 0.14), 0 0 26px rgba(126, 156, 255, 0.16);
  backdrop-filter: blur(6px);
}
.rose .card-icon {
  color: #ffc4df;
  background: rgba(245, 167, 211, 0.06);
  box-shadow: inset 0 1px rgba(255, 255, 255, 0.1), 0 0 26px rgba(245, 167, 211, 0.1);
}
.violet .card-icon {
  color: #ced0ff;
  background: rgba(159, 145, 255, 0.06);
  box-shadow: inset 0 1px rgba(255, 255, 255, 0.1), 0 0 26px rgba(159, 145, 255, 0.12);
}
.sky .card-icon {
  color: #bde6ff;
  background: rgba(125, 203, 255, 0.06);
  box-shadow: inset 0 1px rgba(255, 255, 255, 0.1), 0 0 26px rgba(125, 203, 255, 0.14);
}
.card-content {
  display: grid;
  gap: 7px;
  min-width: 0;
}
.card-content em {
  font-style: normal;
  font: 9px 'DM Mono', monospace;
  letter-spacing: 0.12em;
  color: #aebde9;
}
.card-content strong {
  font-size: 18px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.card-flag,
.card-meta .flag {
  font: 9px 'DM Mono', monospace;
  font-style: normal;
  letter-spacing: 0.08em;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(168, 186, 235, 0.24);
  color: #aebde9;
}
.card-flag.private {
  color: #ffd9a8;
  border-color: rgba(244, 208, 63, 0.4);
}
.card-flag.pinned {
  color: #d8ccff;
  border-color: rgba(176, 110, 224, 0.5);
}
.card-meta .flag.generating {
  color: #8fd8ff;
  border-color: rgba(125, 203, 255, 0.45);
}
.card-meta .flag.failed {
  color: #ff9aa8;
  border-color: rgba(246, 127, 138, 0.45);
}
.card-meta .flag.online {
  color: #8ff0be;
  border-color: rgba(51, 217, 178, 0.45);
}
.card-desc {
  max-width: 260px;
  color: #aeb7d3;
  line-height: 1.65;
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
.card-actions {
  margin-left: auto;
  align-self: flex-start;
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-action-btn.pin {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(168, 186, 235, 0.24);
  border-radius: 9px;
  background: rgba(11, 18, 38, 0.4);
  color: #8c9abc;
  font-size: 13px;
  cursor: pointer;
  opacity: 0;
  transition: 0.2s;
}
.app-card:hover .card-action-btn.pin,
.card-action-btn.pin:focus-visible {
  opacity: 1;
}
.card-action-btn.pin:hover {
  background: rgba(135, 150, 246, 0.16);
  color: #fff;
}
.card-arrow {
  color: #cbd7ff;
  font-size: 24px;
  cursor: pointer;
  transition: transform 0.3s ease;
}
.app-card:hover .card-arrow {
  transform: translate(3px, -3px);
}
</style>
