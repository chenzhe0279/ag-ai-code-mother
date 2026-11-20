<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { App } from 'ant-design-vue'
import {
  AppstoreOutlined,
  ArrowDownOutlined,
  ArrowRightOutlined,
  ArrowUpOutlined,
  CodeOutlined,
  FolderOpenOutlined,
  FormOutlined,
  LayoutOutlined,
} from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  addApp,
  deleteApp,
  getAppVoById,
  listGoodAppVoByPage,
  listMyAppVoByPage,
  pinApp,
  unpinApp,
} from '@/api/appController'
import { formatCodeGenType } from '@/utils/codeGenTypes'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const { message } = App.useApp()

const prompt = ref('')
const promptInput = ref<HTMLTextAreaElement>()
const creating = ref(false)
const visibility = ref<'public' | 'private'>('public')

const examples = [
  {
    label: '个人博客',
    prompt:
      '创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能、评论系统和个人简介页面。采用简洁的设计风格，支持响应式布局，文章支持Markdown格式，首页展示最新文章和热门推荐。',
  },
  {
    label: '企业官网',
    prompt:
      '设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。采用商务风格的设计，包含轮播图、产品展示卡片、团队介绍、客户案例展示，支持多语言切换和在线客服功能。',
  },
  {
    label: '在线商城',
    prompt:
      '构建一个功能完整的在线商城，包含商品展示、购物车、用户注册登录、订单管理、支付结算等功能。设计现代化的商品卡片布局，支持商品搜索筛选、用户评价、优惠券系统和会员积分功能。',
  },
  {
    label: '作品集',
    prompt:
      '制作一个精美的作品展示网站，适合设计师、摄影师、艺术家等创作者。包含作品画廊、项目详情页、个人简历、联系方式等模块。采用瀑布流或网格布局展示作品，支持图片放大预览和作品分类筛选。',
  },
]

const pageSize = 6

function createAppList(fetchPage: typeof listMyAppVoByPage) {
  const state = reactive({
    records: [] as API.AppVO[],
    page: 1,
    total: 0,
    status: 'loading' as 'loading' | 'ready' | 'error',
  })
  let requestId = 0

  async function load(page = state.page) {
    const currentRequest = ++requestId
    state.page = page
    state.status = 'loading'

    try {
      const { data } = await fetchPage({
        pageNum: page,
        pageSize,
        sortField: 'createTime',
        sortOrder: 'desc',
      })
      if (requestId !== currentRequest) return
      if (data.code !== 0 || !data.data) {
        state.status = 'error'
        return
      }

      const total = data.data.totalRow ?? 0
      const lastPage = Math.max(1, Math.ceil(total / pageSize))
      if (page > lastPage) {
        await load(lastPage)
        return
      }

      state.records = data.data.records ?? []
      state.total = total
      state.status = 'ready'
    } catch {
      if (requestId === currentRequest) state.status = 'error'
    }
  }

  function reset() {
    requestId++
    state.records = []
    state.page = 1
    state.total = 0
    state.status = 'loading'
  }

  return { state, load, reset }
}

const myApps = createAppList(listMyAppVoByPage)
const featuredApps = createAppList(listGoodAppVoByPage)
const sections = [
  {
    id: 'my-apps',
    title: '我的作品',
    description: '继续编辑和管理你创建的应用。',
    featured: false,
    list: myApps,
  },
  {
    id: 'featured',
    title: '精选案例',
    description: '浏览社区里的优秀作品，发现下一次创作的灵感。',
    featured: true,
    list: featuredApps,
  },
]

const dockLinks = [
  { id: 'create', label: '开始创建', icon: FormOutlined },
  { id: 'my-apps', label: '我的作品', icon: FolderOpenOutlined },
  { id: 'featured', label: '精选案例', icon: AppstoreOutlined },
]

function setPrompt(value: string) {
  if (creating.value) return
  prompt.value = value
  promptInput.value?.focus()
}

function handlePromptEnter(event: KeyboardEvent) {
  if (event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  void createApp()
}

async function createApp() {
  if (creating.value) return
  const initPrompt = prompt.value.trim()
  if (!initPrompt) {
    message.warning('请输入应用描述')
    promptInput.value?.focus()
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const { data } = await addApp({ initPrompt, visibility: visibility.value })
    if (data.code !== 0 || !data.data) {
      message.error(data.message || '创建失败，请重试')
      return
    }

    const appId = String(data.data)
    let typeLabel = ''
    try {
      const detail = await getAppVoById({ id: data.data })
      if (detail.data.code === 0 && detail.data.data?.codeGenType) {
        typeLabel = formatCodeGenType(detail.data.data.codeGenType)
      }
    } catch (error) {
      console.error('查询应用生成类型失败：', error)
    }
    message.success(typeLabel ? '应用创建成功，已为你选择「' + typeLabel + '」' : '应用创建成功')
    await router.push('/app/chat/' + appId)
  } catch {
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

function viewChat(appId: string | number | undefined) {
  if (appId) void router.push({ path: '/app/chat/' + appId, query: { view: '1' } })
}

function viewWork(app: API.AppVO) {
  if (app.deployKey) window.open(getDeployUrl(app.deployKey), '_blank', 'noopener,noreferrer')
}

function reloadLists() {
  if (!loginUserStore.loginUser.id) return
  void myApps.load()
  void featuredApps.load()
}

async function handlePinToggle(app: API.AppVO) {
  if (!app.id) return
  const willPin = app.priority !== 999
  try {
    const { data } = await (willPin ? pinApp : unpinApp)({ appId: app.id })
    if (data.code !== 0) {
      message.error(data.message || '操作失败，请重试')
      return
    }
    message.success(willPin ? '置顶成功' : '已取消置顶')
    reloadLists()
  } catch {
    message.error('操作失败，请重试')
  }
}

async function handleDeleteApp(app: API.AppVO) {
  if (!app.id) return
  try {
    const { data } = await deleteApp({ id: app.id })
    if (data.code !== 0) {
      message.error(data.message || '删除失败，请重试')
      return
    }
    message.success('删除成功')
    reloadLists()
  } catch {
    message.error('删除失败，请重试')
  }
}

watch(
  () => loginUserStore.loginUser.id,
  () => {
    myApps.reset()
    featuredApps.reset()
    reloadLists()
  },
  { immediate: true },
)
</script>

<template>
  <div class="home workspace-page">
    <section id="create" class="home-hero" aria-labelledby="home-title">
      <div class="hero-main">
        <h1 id="home-title">一句描述，<span>生成完整应用。</span></h1>
        <p class="hero-description">
          说说你想做什么。AI 帮你生成网页，你可以实时预览、继续修改，完成后直接上线。
        </p>

        <form class="composer glass-panel" @submit.prevent="createApp">
          <label for="app-prompt">描述你的应用</label>
          <textarea
            id="app-prompt"
            ref="promptInput"
            v-model="prompt"
            maxlength="1000"
            rows="3"
            placeholder="例如，帮我创建一个展示摄影作品的网站……"
            :readonly="creating"
            @keydown.enter.exact="handlePromptEnter"
          />
          <div class="composer-bar">
            <a-radio-group
              v-model:value="visibility"
              aria-label="可见范围"
              size="small"
              button-style="solid"
              :disabled="creating"
            >
              <a-radio-button value="public">公开</a-radio-button>
              <a-radio-button value="private">私有</a-radio-button>
            </a-radio-group>
            <a-button type="primary" html-type="submit" :loading="creating">
              <template #icon><ArrowUpOutlined /></template>
              {{ creating ? '正在创建' : '生成应用' }}
            </a-button>
          </div>
        </form>

        <div class="prompt-examples">
          <span>试试这些</span>
          <button
            v-for="example in examples"
            :key="example.label"
            type="button"
            :disabled="creating"
            @click="setPrompt(example.prompt)"
          >
            {{ example.label }}
          </button>
        </div>
      </div>

      <aside class="showcase glass-panel" aria-labelledby="showcase-title">
        <div class="showcase-art" aria-hidden="true">
          <div class="art-orbit"></div>
          <div class="art-window">
            <div class="art-window-bar"><i></i><i></i><i></i></div>
            <div class="art-window-body">
              <div class="art-window-hero"><span></span><span></span><span></span></div>
              <div class="art-window-tiles"><i></i><i></i><i></i></div>
            </div>
          </div>
          <span class="art-chip art-chip--layout"><LayoutOutlined /></span>
          <span class="art-chip art-chip--code"><CodeOutlined /></span>
        </div>
        <div class="showcase-copy">
          <div>
            <h2 id="showcase-title">看看别人做了什么</h2>
            <p>从真实作品里找灵感。</p>
          </div>
          <RouterLink to="/#featured" class="showcase-link" aria-label="浏览精选案例">
            <ArrowDownOutlined />
          </RouterLink>
        </div>
      </aside>
    </section>

    <template v-for="section in sections" :key="section.id">
      <section
        v-if="section.featured || loginUserStore.loginUser.id"
        :id="section.id"
        class="collection glass-panel"
        :aria-labelledby="section.id + '-title'"
        :aria-busy="!!loginUserStore.loginUser.id && section.list.state.status === 'loading'"
      >
        <header class="collection-head">
          <div>
            <h2 :id="section.id + '-title'">{{ section.title }}</h2>
            <p>{{ section.description }}</p>
          </div>
          <span v-if="section.list.state.status === 'ready'" class="collection-count">
            {{ section.list.state.total }} 个应用
          </span>
        </header>

        <div v-if="!loginUserStore.loginUser.id" class="collection-state">
          <p>登录后查看精选案例</p>
          <RouterLink to="/user/login">去登录 <ArrowRightOutlined /></RouterLink>
        </div>
        <div
          v-else-if="section.list.state.status === 'loading'"
          class="app-grid"
          aria-label="正在加载"
        >
          <div v-for="index in 3" :key="index" class="card-skeleton">
            <a-skeleton active :title="false" :paragraph="{ rows: 3 }" />
          </div>
        </div>
        <div
          v-else-if="section.list.state.status === 'error'"
          class="collection-state"
          role="status"
        >
          <p>{{ section.title }}加载失败</p>
          <a-button @click="section.list.load()">重试</a-button>
        </div>
        <div v-else-if="!section.list.state.records.length" class="collection-state">
          <a-empty :description="section.featured ? '暂无精选案例' : '还没有创建应用'" />
          <RouterLink v-if="!section.featured" to="/#create"
            >创建第一个应用 <ArrowRightOutlined
          /></RouterLink>
        </div>
        <template v-else>
          <div class="app-grid">
            <AppCard
              v-for="app in section.list.state.records"
              :key="app.id"
              :app="app"
              @view-chat="viewChat"
              @view-work="viewWork"
              @pin-toggle="handlePinToggle"
              @delete-app="handleDeleteApp"
            />
          </div>
          <a-pagination
            v-if="section.list.state.total > pageSize"
            class="pagination"
            :current="section.list.state.page"
            :page-size="pageSize"
            :total="section.list.state.total"
            :show-size-changer="false"
            :show-less-items="true"
            @change="section.list.load"
          />
        </template>
      </section>
    </template>

    <nav class="home-dock glass-panel" aria-label="首页区块">
      <template v-for="item in dockLinks" :key="item.id">
        <RouterLink
          v-if="item.id !== 'my-apps' || loginUserStore.loginUser.id"
          :to="{ path: '/', hash: '#' + item.id }"
          :aria-current="
            route.hash === '#' + item.id || (!route.hash && item.id === 'create')
              ? 'location'
              : undefined
          "
        >
          <component :is="item.icon" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </template>
    </nav>
  </div>
</template>

<style scoped>
.home {
  padding-block: clamp(42px, 7vw, 94px) 8px;
}
.home :is(#create, #my-apps, #featured) {
  scroll-margin-top: 98px;
}

.home-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  align-items: end;
  gap: clamp(24px, 4vw, 62px);
  margin-bottom: clamp(34px, 5.2vw, 64px);
}

.hero-main {
  min-width: 0;
}

.hero-main h1 {
  color: var(--text);
  font-size: clamp(37px, 5.2vw, 76px);
  font-weight: 760;
  line-height: 1.08;
  letter-spacing: -0.061em;
}

.hero-main h1 span {
  display: block;
  color: #566377;
}

.hero-description {
  max-width: 46ch;
  margin: 17px 0 24px;
  color: var(--text-dim);
  font-size: clamp(14px, 1.45vw, 17px);
  line-height: 1.7;
}

.composer {
  padding: 17px 18px 13px;
  border-radius: 22px;
}
.composer label {
  color: var(--text);
  font-size: 14px;
  font-weight: 650;
}

.composer textarea {
  display: block;
  width: 100%;
  min-height: 76px;
  margin-top: 8px;
  padding: 3px 2px;
  border: 0;
  border-radius: 6px;
  outline-offset: 1px;
  background: transparent;
  color: var(--text);
  font: inherit;
  line-height: 1.65;
  resize: vertical;
}

.composer textarea::placeholder {
  color: var(--text-faint);
}
.composer-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 11px;
  border-top: 1px solid var(--line-soft);
}
.composer-bar :deep(.ant-btn-primary) {
  padding-inline: 17px;
  font-weight: 600;
}

.prompt-examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  margin-top: 13px;
  color: var(--text-faint);
  font-size: 12px;
}
.prompt-examples > span {
  margin-right: 3px;
}
.prompt-examples button {
  min-height: 30px;
  padding-inline: 10px;
  border: 1px solid var(--glass-edge);
  border-radius: 15px;
  color: var(--text-dim);
  background: #f7f9fc;
  font: inherit;
  cursor: pointer;
  transition:
    background 180ms ease,
    color 180ms ease;
}
.prompt-examples button:hover {
  background: #fff;
  color: var(--accent);
}
.prompt-examples button:disabled {
  cursor: wait;
}

.showcase {
  overflow: hidden;
  padding: 10px;
}

.showcase-art {
  position: relative;
  display: grid;
  min-height: clamp(180px, 23vw, 315px);
  overflow: hidden;
  place-items: center;
  isolation: isolate;
  border: 1px solid var(--glass-edge);
  border-radius: 21px;
  background:
    radial-gradient(ellipse at 85% 15%, #fff 0%, transparent 60%),
    linear-gradient(145deg, #e9edf3, #ced7e3 70%, #e8edf4);
}

.art-orbit {
  position: absolute;
  width: 112%;
  height: 72%;
  border: 1px solid #ffffffb0;
  border-radius: 50%;
  box-shadow:
    inset 0 0 22px #ffffff3b,
    0 0 22px #ffffff5c;
  transform: rotate(-24deg);
}

.art-window {
  z-index: 1;
  width: 62%;
  padding: 1px;
  border: 1px solid #fff;
  border-radius: 15px;
  background: linear-gradient(150deg, #f9fbff, #e1e8f1);
  box-shadow:
    15px 24px 38px -20px #52627e8a,
    inset 0 0 0 2px #ffffffc4;
  transform: rotate(-8deg);
}

.art-window-bar {
  display: flex;
  gap: 4px;
  padding: 10px;
  border-bottom: 1px solid #dce3ec;
}
.art-window-bar i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #8390a3;
}
.art-window-body {
  display: grid;
  gap: 8px;
  padding: 9px;
}
.art-window-hero {
  display: grid;
  align-content: center;
  justify-items: start;
  gap: 6px;
  min-height: clamp(52px, 6vw, 88px);
  padding: 10px;
  border-radius: 7px;
  background: linear-gradient(135deg, #d8e0eb, #f2f5fa);
}
.art-window-hero span {
  width: 60%;
  height: 5px;
  border-radius: 999px;
  background: #8391a5;
}
.art-window-hero span:nth-child(2) {
  width: 42%;
  background: #a1afbf;
}
.art-window-hero span:nth-child(3) {
  width: 24%;
  height: 4px;
  background: #71849d;
}
.art-window-tiles {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}
.art-window-tiles i {
  height: clamp(17px, 2.8vw, 40px);
  border-radius: 5px;
  background: #f8fafd;
}

.art-chip {
  position: absolute;
  z-index: 2;
  display: grid;
  width: 45px;
  height: 45px;
  place-items: center;
  border: 1px solid #fff;
  border-radius: 14px;
  background: linear-gradient(145deg, #fefeff, #e4eaf3);
  box-shadow: 0 10px 23px -12px #52627e8a;
  color: #4b5c74;
  font-size: 20px;
}
.art-chip--layout {
  top: 16%;
  left: 13%;
  transform: rotate(-10deg);
}
.art-chip--code {
  right: 12%;
  bottom: 15%;
  transform: rotate(9deg);
}

.showcase-copy {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 16px 11px 9px;
}
.showcase-copy h2 {
  color: var(--text);
  font-size: clamp(16px, 2vw, 21px);
  font-weight: 700;
}
.showcase-copy p {
  margin-top: 3px;
  color: var(--text-dim);
  font-size: 13px;
}
.showcase-link {
  display: grid;
  width: 40px;
  height: 40px;
  flex: none;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: #fff;
  color: var(--accent);
  transition: background 180ms ease;
}
.showcase-link:hover {
  background: var(--accent-soft);
}

.collection {
  margin-bottom: 19px;
  padding: clamp(15px, 2.3vw, 28px);
}
.collection-head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
}
.collection-head h2 {
  color: var(--text);
  font-size: clamp(23px, 3vw, 30px);
  font-weight: 730;
  letter-spacing: -0.04em;
}
.collection-head p {
  margin-top: 4px;
  color: var(--text-dim);
}
.collection-count {
  padding: 5px 11px;
  border: 1px solid var(--glass-edge);
  border-radius: 18px;
  background: #ffffffa6;
  color: var(--text-dim);
  font-size: 12px;
}
.app-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 15px;
}
.card-skeleton {
  min-height: 144px;
  padding: 18px;
  border: 1px solid var(--glass-edge);
  border-radius: 17px;
  background: #ffffff91;
}
.collection-state {
  display: grid;
  justify-items: center;
  gap: 9px;
  padding: 24px 8px;
  color: var(--text-dim);
}
.collection-state :deep(.ant-empty) {
  margin: 0;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  margin-top: 20px;
}

.home-dock {
  position: fixed;
  z-index: 25;
  left: 50%;
  bottom: calc(14px + env(safe-area-inset-bottom));
  display: flex;
  max-width: calc(100% - 22px);
  gap: 4px;
  padding: 5px;
  border-radius: 23px;
  box-shadow:
    0 12px 36px -14px #49596f61,
    inset 0 1px 0 #fff;
  transform: translateX(-50%);
}
.home-dock a {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: 17px;
  color: var(--text-dim);
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
  transition:
    background 180ms ease,
    color 180ms ease;
}
.home-dock a:hover {
  background: #fff;
  color: var(--accent);
}
.home-dock a[aria-current] {
  border-color: #fff;
  background: var(--accent-soft);
  color: var(--accent);
}
.home-dock :deep(.anticon) {
  font-size: 16px;
}

@media (max-width: 820px) {
  .home-hero {
    gap: 22px;
  }
  .app-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 720px) {
  .home {
    padding-top: 30px;
  }
  .home :is(#create, #my-apps, #featured) {
    scroll-margin-top: 12px;
  }
  .home-hero {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .hero-description {
    margin: 13px 0 19px;
  }
  .showcase-art {
    min-height: 145px;
  }
  .art-window {
    width: min(58%, 220px);
  }
}
@media (max-width: 500px) {
  .app-grid {
    grid-template-columns: 1fr;
  }
  .home-dock a {
    min-height: 44px;
  }
}
</style>
