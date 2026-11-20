<script setup lang="ts">
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'

const route = useRoute()
</script>

<template>
  <div class="basic-layout" :class="{ 'basic-layout--home': route.path === '/' }">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <GlobalHeader />
    <main id="main-content" class="main-content" tabindex="-1">
      <router-view />
    </main>
    <GlobalFooter />
  </div>
</template>

<style scoped>
.basic-layout {
  display: flex;
  min-height: 100svh;
  flex-direction: column;
  gap: 0;
}

.basic-layout--home {
  padding-bottom: calc(78px + env(safe-area-inset-bottom));
}

.main-content {
  min-width: 0;
  flex: 1;
}
</style>
