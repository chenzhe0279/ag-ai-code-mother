<script setup lang="ts">
import { CodeOutlined, KeyOutlined } from '@ant-design/icons-vue'

defineProps<{ title: string }>()
</script>

<template>
  <div class="auth-page workspace-page">
    <div class="auth-intro">
      <span class="auth-mark" aria-hidden="true"><CodeOutlined /></span>
      <h1>把想法变成<span>可以用的网站。</span></h1>
      <p>用自然语言描述需求，在同一个工作台生成、预览和发布你的应用。</p>
    </div>
    <section class="auth-card glass-panel" aria-labelledby="auth-title">
      <header class="auth-heading">
        <span class="auth-icon"><KeyOutlined /></span>
        <h2 id="auth-title">{{ title }}</h2>
      </header>
      <slot />
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  display: grid;
  width: min(1080px, var(--workspace-width));
  min-height: min(640px, calc(100svh - 148px));
  grid-template-columns: minmax(0, 1fr) minmax(280px, 410px);
  align-items: center;
  gap: clamp(24px, 6vw, 92px);
  padding-block: clamp(30px, 5vw, 66px);
}
.auth-intro h1 {
  color: var(--text);
  font-size: clamp(35px, 5.2vw, 68px);
  font-weight: 750;
  letter-spacing: -0.058em;
  line-height: 1.1;
}
.auth-intro h1 span {
  display: block;
  color: #566377;
}
.auth-intro p {
  max-width: 33ch;
  margin-top: 14px;
  color: var(--text-dim);
  line-height: 1.7;
}
.auth-mark,
.auth-icon {
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border: 1px solid var(--glass-edge);
  border-radius: 17px;
  background: linear-gradient(145deg, #fff, #dfe6ef);
  box-shadow: var(--glass-shadow);
  color: #4b5c74;
  font-size: 23px;
}
.auth-mark {
  margin-bottom: 22px;
}
.auth-card {
  padding: clamp(19px, 3.3vw, 36px);
}
.auth-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 22px;
}
.auth-heading h2 {
  margin: 0;
  color: var(--text);
  font-size: 24px;
  font-weight: 710;
}
.auth-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  font-size: 17px;
}
.auth-card :deep(.auth-switch) {
  margin-top: 4px;
  color: var(--text-dim);
  text-align: center;
}

@media (max-width: 720px) {
  .auth-page {
    grid-template-columns: 1fr;
  }
  .auth-mark {
    display: none;
  }
  .auth-intro h1 span {
    display: inline;
  }
}
</style>
