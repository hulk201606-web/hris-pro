<script setup lang="ts">
import { InboxOutlined } from '@ant-design/icons-vue';

/**
 * 统一空态展示（列表/取数面板共用）。
 * 一句话说明「为什么空」+ 可选操作指引，避免各页各写一套 a-empty 文案。
 */
withDefaults(
  defineProps<{
    /** 主文案，如「暂无数据」 */
    description: string;
    /** 操作指引，如「点击右上角「新建」添加第一条」 */
    hint?: string;
  }>(),
  { hint: '' },
);
</script>

<template>
  <div class="empty-hint">
    <InboxOutlined class="empty-icon" />
    <p class="empty-text">{{ description }}</p>
    <p v-if="hint" class="empty-tip">{{ hint }}</p>
  </div>
</template>

<style scoped>
.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-8) var(--space-4);
}

.empty-icon {
  font-size: 28px;
  color: var(--color-text-muted);
}

.empty-text {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.empty-tip {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
