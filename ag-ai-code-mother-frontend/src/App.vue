<script setup lang="ts">
import { theme as antdTheme } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import BasicLayout from '@/layouts/BasicLayout.vue'

const themeConfig = {
  algorithm: antdTheme.defaultAlgorithm,
  token: {
    colorPrimary: '#2459cf',
    colorLink: '#2459cf',
    colorSuccess: '#20704e',
    colorWarning: '#895605',
    colorError: '#b42333',
    colorText: '#202631',
    colorTextSecondary: '#4b5565',
    colorTextTertiary: '#5f6a7a',
    colorTextPlaceholder: '#5f6a7a',
    colorBgContainer: '#ffffff',
    colorBgElevated: '#fdfdff',
    colorBorder: '#c8d0dd',
    colorBorderSecondary: '#e0e5ed',
    borderRadius: 12,
    controlHeight: 38,
    fontFamily: 'var(--font-sans)',
  },
  components: {
    Table: { headerBg: '#f0f3f8', rowHoverBg: '#f2f5fa' },
  },
}
</script>

<template>
  <a-config-provider :theme="themeConfig" :locale="zhCN">
    <a-app>
      <BasicLayout />
    </a-app>
  </a-config-provider>
</template>
