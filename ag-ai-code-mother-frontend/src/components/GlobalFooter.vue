<template>
  <a-layout-footer class="footer reveal" style="--rd: 1820ms">
    <div class="footer-content">
      <span>© {{ new Date().getFullYear() }} AI 零码</span>
      <span>一句话生成应用 · 探索无限可能</span>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
// 无需额外的响应式数据
</script>

<style scoped>
.footer {
  padding: 16px 24px 20px;
  margin-top: 24px;
}

.footer-content {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  color: #7181ac;
  font: 10px 'DM Mono', monospace;
  letter-spacing: 0.08em;
}

@media (max-width: 560px) {
  .footer-content {
    display: block;
    line-height: 2;
    text-align: center;
  }
}
</style>
