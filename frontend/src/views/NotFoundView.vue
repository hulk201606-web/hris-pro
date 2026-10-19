<script setup lang="ts">

/**
 * 404 页面：匹配未命中路由时展示，提供返回自助首页按钮。
 */
import { useRouter } from 'vue-router';

const router = useRouter();
</script>

<template>
  <div class="not-found">
    <a-result status="404" title="404" sub-title="抱歉，您访问的页面不存在或已被移动">
      <template #extra>
        <a-button type="primary" @click="router.push('/self/home')">返回自助首页</a-button>
      </template>
    </a-result>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 70vh;
}
</style>
