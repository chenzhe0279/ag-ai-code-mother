<template>
  <!-- 全局动态背景：所有页面共用 -->
  <video
    v-if="currentBackground.type === 'video'"
    :key="currentBackground.id"
    class="app-bg-video"
    autoplay
    muted
    loop
    playsinline
    webkit-playsinline
    preload="auto"
    :poster="currentBackground.poster"
    aria-hidden="true"
    tabindex="-1"
  >
    <source :src="currentBackground.src" type="video/mp4" />
  </video>
  <div
    v-else
    :key="currentBackground.id"
    class="app-bg-image"
    :style="{ backgroundImage: `url(${currentBackground.src})` }"
    aria-hidden="true"
  ></div>

  <!-- 背景切换控件 -->
  <div class="bg-switcher" role="group" aria-label="背景切换">
    <button class="bg-switch-btn" title="上一个背景" type="button" @click="prevBackground">‹</button>
    <button class="bg-switch-label" title="点击切换背景" type="button" @click="nextBackground">
      {{ currentBackground.name }}
    </button>
    <button class="bg-switch-btn" title="下一个背景" type="button" @click="nextBackground">›</button>
    <button
      class="bg-switch-btn"
      :class="{ active: bgAuto }"
      :title="bgAuto ? '停止自动轮播' : '自动轮播背景（10 秒切换）'"
      type="button"
      @click="toggleBackgroundAuto"
    >
      {{ bgAuto ? '❚❚' : '▶' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

// 背景：蜡笔小新·湖边之夜（默认初始背景）/ 彗星日落（视频）/ 星空流星 / 彗星蓝天
const BACKGROUNDS_KEY = 'ag_ai_background'
const BACKGROUND_AUTO_KEY = 'ag_ai_background_auto'
const backgrounds = [
  { id: 'xiaoxin-night', name: '蜡笔小新·湖边之夜', type: 'image', src: '/background/xiaoxin-night.webp' },
  { id: 'comet', name: '彗星日落', type: 'video', src: '/background/comet.mp4', poster: '/background/comet-poster.webp' },
  { id: 'stars', name: '星空流星', type: 'image', src: '/background/stars.webp' },
  { id: 'starry-eyes', name: '彗星蓝天', type: 'image', src: '/background/starry-eyes.webp' },
]

function loadBackgroundIndex() {
  try {
    const raw = Number(localStorage.getItem(BACKGROUNDS_KEY))
    if (Number.isInteger(raw) && raw >= 0 && raw < backgrounds.length) return raw
  } catch {
    // 存储不可用时使用默认背景
  }
  return 0
}

const bgIndex = ref(loadBackgroundIndex())
const bgAuto = ref(false)
let bgAutoTimer: number | null = null
const currentBackground = computed(() => backgrounds[bgIndex.value])

function saveBackground() {
  try {
    localStorage.setItem(BACKGROUNDS_KEY, String(bgIndex.value))
    localStorage.setItem(BACKGROUND_AUTO_KEY, bgAuto.value ? '1' : '0')
  } catch {
    // 存储不可用时忽略（隐私模式等）
  }
}

function nextBackground() {
  bgIndex.value = (bgIndex.value + 1) % backgrounds.length
  saveBackground()
}

function prevBackground() {
  bgIndex.value = (bgIndex.value - 1 + backgrounds.length) % backgrounds.length
  saveBackground()
}

function stopAutoTimer() {
  if (bgAutoTimer !== null) {
    window.clearInterval(bgAutoTimer)
    bgAutoTimer = null
  }
}

function toggleBackgroundAuto() {
  bgAuto.value = !bgAuto.value
  stopAutoTimer()
  if (bgAuto.value) {
    bgAutoTimer = window.setInterval(nextBackground, 10_000)
  }
  saveBackground()
}

onMounted(() => {
  bgAuto.value = localStorage.getItem(BACKGROUND_AUTO_KEY) === '1'
  if (bgAuto.value) {
    bgAutoTimer = window.setInterval(nextBackground, 10_000)
  }
})

onUnmounted(() => {
  stopAutoTimer()
})
</script>

<style scoped>
.app-bg-video,
.app-bg-image {
  position: fixed;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  filter: saturate(1.08) brightness(1.02);
  animation: bg-fade 0.45s ease, bg-kenburns 26s ease-in-out infinite;
}

.app-bg-video {
  object-fit: cover;
  animation: bg-fade 0.45s ease;
}

.app-bg-image {
  background-repeat: no-repeat;
  background-position: center;
  background-size: cover;
}

@keyframes bg-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes bg-kenburns {
  0% { transform: scale(1.02) translate(0, 0); }
  50% { transform: scale(1.14) translate(-1.2%, 1%); }
  100% { transform: scale(1.02) translate(0, 0); }
}

.bg-switcher {
  position: fixed;
  right: 18px;
  bottom: 18px;
  z-index: 60;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px;
  border: 1px solid rgba(168, 186, 235, 0.24);
  border-radius: 999px;
  background: rgba(11, 18, 38, 0.4);
  backdrop-filter: blur(14px);
  box-shadow: 0 8px 28px rgba(2, 5, 17, 0.3);
}

.bg-switch-btn,
.bg-switch-label {
  border: 0;
  background: transparent;
  color: #cdd8f5;
  cursor: pointer;
  transition: 0.2s;
}

.bg-switch-btn {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-size: 14px;
  line-height: 1;
}

.bg-switch-btn:hover {
  background: rgba(135, 150, 246, 0.14);
  color: #fff;
}

.bg-switch-btn.active {
  color: #8ff0be;
  box-shadow: 0 0 10px rgba(143, 240, 190, 0.35);
}

.bg-switch-label {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: 0.05em;
  white-space: nowrap;
}

.bg-switch-label:hover {
  background: rgba(135, 150, 246, 0.1);
  color: #fff;
}

@media (max-width: 560px) {
  .bg-switcher {
    right: 10px;
    bottom: 10px;
    padding: 4px;
  }
  .bg-switch-btn {
    width: 24px;
    height: 24px;
    font-size: 13px;
  }
  .bg-switch-label {
    padding: 4px 8px;
    font-size: 11px;
  }
}
</style>
