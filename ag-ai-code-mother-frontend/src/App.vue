<script setup lang="ts">
import { theme as antdTheme } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import BasicLayout from '@/layouts/BasicLayout.vue'
import BackgroundSwitcher from '@/components/BackgroundSwitcher.vue'

// 深空极光主题：AntD 组件统一跟随参考项目的配色体系
const themeConfig = {
  algorithm: antdTheme.darkAlgorithm,
  token: {
    colorPrimary: '#6d7cff',
    colorInfo: '#8f9dff',
    colorBgBase: '#04060f',
    colorBgContainer: '#0e1630',
    colorBgElevated: '#121c3c',
    colorBorder: 'rgba(168, 186, 235, 0.22)',
    colorBorderSecondary: 'rgba(168, 186, 235, 0.14)',
    borderRadius: 10,
    fontFamily: "'Noto Sans SC', system-ui, sans-serif",
  },
}
</script>

<template>
  <!-- 全局动态背景（四套可切换，右下角控件） -->
  <BackgroundSwitcher />
  <a-config-provider :theme="themeConfig" :locale="zhCN">
    <BasicLayout />
  </a-config-provider>
</template>
