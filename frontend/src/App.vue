<script setup lang="ts">
import { appTheme } from './theme/antd-theme';
import zhCN from 'ant-design-vue/es/locale/zh_CN';
</script>

<template>
  <a-config-provider :theme="appTheme" :locale="zhCN">
    <router-view />
  </a-config-provider>
</template>
