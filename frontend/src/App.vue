<script setup lang="ts">

/**
 * 根组件：包裹 Ant Design 主题与中文语言包，内部仅渲染路由出口。
 * 双端（自助端 / 管理端）通过路由前缀 /self 与 /admin 在同一 SPA 内分流。
 */
import { appTheme } from './theme/antd-theme';
import zhCN from 'ant-design-vue/es/locale/zh_CN';
</script>

<template>
  <a-config-provider :theme="appTheme" :locale="zhCN">
    <router-view />
  </a-config-provider>
</template>
