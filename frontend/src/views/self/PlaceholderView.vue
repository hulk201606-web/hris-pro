<script setup lang="ts">

/**
 * 占位页：尚未接入的自助模块（我的薪酬 / 我的假期等）临时展示，
 * 通过 props.title 传入页面名，提示「建设中」。
 */
import { ToolOutlined } from '@ant-design/icons-vue';

defineProps<{ title: string }>();
</script>

<template>
  <a-card :bordered="false" class="placeholder-card">
    <a-result :title="`${title} · 建设中`" sub-title="该模块将在后续迭代中开放">
      <template #icon>
        <ToolOutlined />
      </template>
    </a-result>
  </a-card>
</template>

<style scoped>
.placeholder-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
</style>
