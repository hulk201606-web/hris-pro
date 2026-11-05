<script setup lang="ts">
/**
 * 假期消耗报表（ATT-F-025）：按假种汇总额度、已用与消耗率。
 *
 * period 的取值由额度记录自身的 period 决定（该字段无固定格式约束，
 * 由写入方决定）。默认取当前年月，若后端按季度记账可改为 2026Q4 形式。
 */
import { onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { getLeaveConsumeReport } from '../../../api/attendance-report';
import type { LeaveConsumeItem } from '../../../api/attendance-report';

function currentPeriod(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

const period = ref(currentPeriod());
const rows = ref<LeaveConsumeItem[]>([]);
const loading = ref(false);

async function load() {
  if (!period.value.trim()) return;
  loading.value = true;
  try {
    rows.value = await getLeaveConsumeReport(period.value.trim());
  } catch (e) {
    message.error(e instanceof Error ? e.message : '假期消耗报表加载失败');
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function pct(v?: number): string {
  return v === undefined || v === null ? '-' : `${(v * 100).toFixed(2)}%`;
}

onMounted(load);
</script>

<template>
  <div>
    <a-space class="toolbar">
      <span>期间</span>
      <a-input
        v-model:value="period"
        placeholder="如 2026-11 或 2026Q4"
        style="width: 200px"
        @press-enter="load"
      />
      <a-button type="primary" :loading="loading" @click="load">查询</a-button>
    </a-space>
    <p class="hint">期间格式需与假期额度记录的 period 一致；无数据时请确认该期间已发放额度。</p>

    <a-table
      :data-source="rows"
      :loading="loading"
      row-key="leaveTypeId"
      size="middle"
      :pagination="false"
      :locale="{ emptyText: '该期间暂无额度数据' }"
    >
      <a-table-column key="leaveTypeName" title="假种" data-index="leaveTypeName" :width="200" />
      <a-table-column key="totalQuota" title="总发放" data-index="totalQuota" :width="130" />
      <a-table-column key="usedQuota" title="已使用" data-index="usedQuota" :width="130" />
      <a-table-column key="consumeRate" title="消耗率" :width="130">
        <template #default="{ record }">{{ pct(record.consumeRate) }}</template>
      </a-table-column>
    </a-table>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 8px;
}
.hint {
  margin: 0 0 16px;
  color: var(--color-text-secondary, #666);
  font-size: 12px;
}
</style>
