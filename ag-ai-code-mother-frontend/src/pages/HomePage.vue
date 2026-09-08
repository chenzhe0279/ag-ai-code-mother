<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  addApp,
  listMyAppVoByPage,
  listGoodAppVoByPage,
  pinApp,
  unpinApp,
} from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 用户提示词
const userPrompt = ref('')
const creating = ref(false)
const visibility = ref<'public' | 'private'>('public')

// 我的应用数据
const myApps = ref<API.AppVO[]>([])
const myAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 精选应用数据
const featuredApps = ref<API.AppVO[]>([])
const featuredAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 设置提示词
const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
}

// 创建应用
const createApp = async () => {
  if (!userPrompt.value.trim()) {
    message.warning('请输入应用描述')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const res = await addApp({
      initPrompt: userPrompt.value.trim(),
      visibility: visibility.value,
    })

    if (res.data.code === 0 && res.data.data) {
      message.success('应用创建成功')
      // 跳转到对话页面，确保ID是字符串类型
      const appId = String(res.data.data)
      await router.push(`/app/chat/${appId}`)
    } else {
      message.error('创建失败：' + res.data.message)
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

// 加载我的应用
const loadMyApps = async () => {
  if (!loginUserStore.loginUser.id) {
    return
  }

  try {
    const res = await listMyAppVoByPage({
      pageNum: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载我的应用失败：', error)
  }
}

// 加载精选应用
const loadFeaturedApps = async () => {
  try {
    const res = await listGoodAppVoByPage({
      pageNum: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records || []
      featuredAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载精选应用失败：', error)
  }
}

// 查看对话
const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

// 查看作品
const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    const url = getDeployUrl(app.deployKey)
    window.open(url, '_blank')
  }
}

// 置顶 / 取消置顶
const handlePinToggle = async (app: API.AppVO) => {
  if (!app.id) return
  const willPin = app.priority !== 999
  try {
    const res = willPin ? await pinApp({ appId: app.id }) : await unpinApp({ appId: app.id })
    if (res.data.code === 0) {
      message.success(willPin ? '置顶成功' : '已取消置顶')
      loadMyApps()
      loadFeaturedApps()
    } else {
      message.error('操作失败：' + res.data.message)
    }
  } catch (error) {
    console.error('置顶操作失败：', error)
    message.error('操作失败，请重试')
  }
}

// 页面加载时获取数据
onMounted(() => {
  loadMyApps()
  loadFeaturedApps()
})
</script>

<template>
  <div id="homePage">
    <div class="home-container">
      <!-- Hero 区 -->
      <div class="hero">
        <p class="eyebrow reveal">ZERO-CODE AI, EXPANDED</p>
        <h1 class="hero-title reveal">一句描述，<em>生成完整应用。</em></h1>
        <p class="hero-description reveal">
          一句话轻松创建网站应用，从想法到上线只差一次回车。
        </p>
      </div>

      <!-- 用户提示词输入框 -->
      <div class="input-section reveal">
        <a-textarea
          v-model:value="userPrompt"
          placeholder="帮我创建个人博客网站"
          :rows="4"
          :maxlength="1000"
          class="prompt-input"
          @keydown.enter.exact.prevent="createApp"
        />
        <div class="input-actions">
          <a-button type="primary" shape="circle" size="large" :loading="creating" @click="createApp">
            <template #icon>
              <span class="send-arrow">↑</span>
            </template>
          </a-button>
        </div>
        <div class="visibility-row">
          <span class="visibility-label">可见范围</span>
          <a-radio-group v-model:value="visibility" size="small">
            <a-radio-button value="public">公开</a-radio-button>
            <a-radio-button value="private">私有</a-radio-button>
          </a-radio-group>
        </div>
      </div>

      <!-- 快捷按钮 -->
      <div class="quick-actions reveal">
        <a-button
          type="default"
          @click="
            setPrompt(
              '创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能、评论系统和个人简介页面。采用简洁的设计风格，支持响应式布局，文章支持Markdown格式，首页展示最新文章和热门推荐。',
            )
          "
          >个人博客网站</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。采用商务风格的设计，包含轮播图、产品展示卡片、团队介绍、客户案例展示，支持多语言切换和在线客服功能。',
            )
          "
          >企业官网</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '构建一个功能完整的在线商城，包含商品展示、购物车、用户注册登录、订单管理、支付结算等功能。设计现代化的商品卡片布局，支持商品搜索筛选、用户评价、优惠券系统和会员积分功能。',
            )
          "
          >在线商城</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '制作一个精美的作品展示网站，适合设计师、摄影师、艺术家等创作者。包含作品画廊、项目详情页、个人简历、联系方式等模块。采用瀑布流或网格布局展示作品，支持图片放大预览和作品分类筛选。',
            )
          "
          >作品展示网站</a-button
        >
      </div>

      <!-- 我的作品 -->
      <div v-if="loginUserStore.loginUser.id" class="section">
        <p class="section-eyebrow">MY APPS</p>
        <h2 class="section-title">我的作品</h2>
        <div class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="app.id"
            :app="app"
            @view-chat="viewChat"
            @view-work="viewWork"
            @pin-toggle="handlePinToggle"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="myAppsPage.current"
            v-model:page-size="myAppsPage.pageSize"
            :total="myAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个应用`"
            @change="loadMyApps"
          />
        </div>
      </div>

      <!-- 精选案例 -->
      <div class="section">
        <p class="section-eyebrow">FEATURED</p>
        <h2 class="section-title">精选案例</h2>
        <div class="app-grid">
          <AppCard
            v-for="app in featuredApps"
            :key="app.id"
            :app="app"
            :featured="true"
            @view-chat="viewChat"
            @view-work="viewWork"
            @pin-toggle="handlePinToggle"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="featuredAppsPage.current"
            v-model:page-size="featuredAppsPage.pageSize"
            :total="featuredAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个案例`"
            @change="loadFeaturedApps"
          />
        </div>
      </div>

      <!-- 特性条 -->
      <div class="feature-strip">
        <div class="feature reveal"><b>流式生成</b><span>AI 逐字输出，实时预览</span></div>
        <div class="feature reveal"><b>版本管理</b><span>历史版本一键回退</span></div>
        <div class="feature reveal"><b>一键部署</b><span>生成即可上线访问</span></div>
        <div class="feature reveal"><b>可视编辑</b><span>选中元素精准修改</span></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  width: 100%;
  min-height: calc(100vh - 64px);
}

.home-container {
  width: min(1160px, calc(100% - 64px));
  margin: 0 auto;
  padding: 29px 0 24px;
  display: flex;
  flex-direction: column;
}

/* Hero 区 */
.hero {
  max-width: 820px;
  padding-top: 54px;
  margin-bottom: 44px;
}

/* 入场显现交错延迟（顺序与 DOM 一致，节奏约 1.8s） */
.hero .eyebrow { --rd: 380ms; }
.hero .hero-title { --rd: 520ms; }
.hero .hero-description { --rd: 660ms; }
.input-section { --rd: 800ms; }
.quick-actions { --rd: 940ms; }
.app-grid > :nth-child(1) { --rd: 960ms; }
.app-grid > :nth-child(2) { --rd: 1080ms; }
.app-grid > :nth-child(3) { --rd: 1200ms; }
.app-grid > :nth-child(4) { --rd: 1280ms; }
.app-grid > :nth-child(5) { --rd: 1360ms; }
.app-grid > :nth-child(6) { --rd: 1440ms; }
.feature-strip > .feature:nth-child(1) { --rd: 1560ms; }
.feature-strip > .feature:nth-child(2) { --rd: 1660ms; }
.feature-strip > .feature:nth-child(3) { --rd: 1760ms; }
.feature-strip > .feature:nth-child(4) { --rd: 1860ms; }

.hero-title {
  margin: 0;
  font: 700 clamp(44px, 6.4vw, 80px) / 1.06 'Playfair Display', 'Noto Sans SC', serif;
  letter-spacing: -0.055em;
  color: var(--text);
  text-shadow: 0 8px 28px rgba(0, 0, 0, 0.6);
}

.hero-title em {
  font-weight: 600;
  background: linear-gradient(115deg, #b9c7ff 12%, #c9a8ff 46%, #7ee6cf 88%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: none;
}

.hero-description {
  max-width: 520px;
  margin: 26px 0 0;
  color: var(--text-dim);
  font-size: 16px;
  line-height: 1.9;
}

/* 输入区 */
.input-section {
  position: relative;
  margin: 0 auto 24px;
  max-width: 800px;
  width: 100%;
  padding: 18px 18px 10px;
  border: 1px solid rgba(175, 190, 225, 0.28);
  border-radius: 18px;
  background: rgba(17, 26, 49, 0.42);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(12px);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-section:focus-within {
  border-color: rgba(170, 189, 249, 0.6);
  box-shadow:
    0 0 0 3px rgba(124, 145, 223, 0.12),
    0 14px 40px rgba(0, 0, 0, 0.4);
}

.prompt-input {
  border: none;
  background: transparent;
  color: var(--text);
  font-size: 15px;
}

.prompt-input::placeholder {
  color: #7987a7;
}

.input-actions {
  position: absolute;
  right: 20px;
  bottom: 46px;
}

.send-arrow {
  font-size: 18px;
  line-height: 1;
}

.visibility-row {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  padding: 6px 4px 4px;
}

.visibility-label {
  color: var(--text-faint);
  font: 10px 'DM Mono', monospace;
  letter-spacing: 0.1em;
}

/* 快捷按钮 */
.quick-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 60px;
  flex-wrap: wrap;
}

.quick-actions :deep(.ant-btn) {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 8px 20px;
  height: auto;
  background: rgba(24, 34, 66, 0.32);
  color: #cdd8f5;
  backdrop-filter: blur(8px);
  transition: all 0.25s ease;
}

.quick-actions :deep(.ant-btn:hover) {
  border-color: rgba(190, 205, 255, 0.55);
  color: #fff;
  background: rgba(31, 44, 84, 0.4);
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(109, 124, 255, 0.2);
}

/* 区域标题 */
.section {
  margin-bottom: 60px;
}

.section-eyebrow {
  margin: 0 0 8px;
  color: #c0adff;
  font: 10px 'DM Mono', monospace;
  letter-spacing: 0.14em;
}

.section-title {
  font: 600 30px/1.2 'Playfair Display', 'Noto Sans SC', serif;
  margin: 0 0 28px;
  color: var(--text);
}

/* 应用网格 */
.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}

/* 特性条 */
.feature-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 10px;
}

.feature {
  display: grid;
  gap: 5px;
  padding: 17px 18px;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  background: rgba(15, 23, 48, 0.32);
  backdrop-filter: blur(8px);
  transition: 0.25s ease;
}

.feature:hover {
  border-color: rgba(190, 205, 255, 0.32);
  background: rgba(21, 31, 64, 0.42);
  transform: translateY(-3px);
}

.feature b {
  font: 500 12px 'DM Mono', monospace;
  letter-spacing: 0.08em;
  color: #e4eaff;
}

.feature b::before {
  content: '◆ ';
  color: var(--teal);
  font-size: 9px;
  vertical-align: 1px;
}

.feature span {
  color: #8c9abc;
  font-size: 12px;
}

/* 响应式 */
@media (max-width: 900px) {
  .feature-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 780px) {
  .home-container {
    width: min(100% - 40px, 1160px);
  }
  .hero {
    padding-top: 30px;
    margin-bottom: 32px;
  }
}

@media (max-width: 560px) {
  .home-container {
    width: calc(100% - 32px);
  }
  .app-grid {
    grid-template-columns: 1fr;
  }
  .feature-strip {
    grid-template-columns: 1fr;
  }
  .input-actions {
    bottom: 50px;
  }
}
</style>
