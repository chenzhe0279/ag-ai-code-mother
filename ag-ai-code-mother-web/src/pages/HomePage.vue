<template>
  <div>
    <div class="page-head">
      <div>
        <h1>应用广场</h1>
        <p>这里汇聚了精选的 AI 生成应用，选择心仪的应用立即体验。</p>
      </div>
      <a-button type="primary" @click="router.push('/my')">创建我的应用</a-button>
    </div>

    <a-spin :spinning="loading">
      <a-empty v-if="!loading && apps.length === 0" description="暂无精选应用" />
      <a-row v-else :gutter="[16, 16]">
        <a-col v-for="app in apps" :key="app.id" :xs="24" :sm="12" :md="8">
          <AppCard :app="app" />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listGoodAppByPage, type AppVO } from '@/api/app'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const loading = ref(false)
const apps = ref<AppVO[]>([])
const pageNum = ref(1)
const pageSize = 9
const total = ref(0)

const loadApps = async () => {
  loading.value = true
  try {
    const res = await listGoodAppByPage({ pageNum: pageNum.value, pageSize })
    apps.value = res.records || []
    total.value = res.totalRow || 0
  } catch {
    // 提示已由拦截器处理
  } finally {
    loading.value = false
  }
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

.pagination {
  margin-top: 24px;
  text-align: center;
}
</style>
