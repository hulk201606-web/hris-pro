<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import EmptyHint from '../EmptyHint.vue';
import { errorText } from '../crud-helpers';
import { listSchedulerLogs } from '../../../api/scheduler';
import type { SchedulerLog } from '../../../api/scheduler';

/**
 * 调度执行日志面板（PLT-F-011）。
 * 单独成组件：父页负责注册项与操作，这里只负责「最近跑得怎么样」，
 * 并通过 expose reload 让「立即执行」后能定向刷新日志而不重载整页。
 */
const rows = ref<SchedulerLog[]>([]);
const loading = ref(false);

const columns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '任务键', dataIndex: 'taskKey', key: 'taskKey', width: 160 },
  { title: '触发', dataIndex: 'triggerType', key: 'triggerType', width: 90 },
  { title: '结果', key: 'status', width: 100 },
  { title: '重试', dataIndex: 'retryNo', key: 'retryNo', width: 70 },
  { title: '耗时(ms)', dataIndex: 'durationMs', key: 'durationMs', width: 100 },
  { title: '摘要', dataIndex: 'message', key: 'message' },
];

async function reload() {
  loading.value = true;
  try {
    rows.value = (await listSchedulerLogs()).records ?? [];
  } catch (e) {
    message.error(errorText(e, '执行日志加载失败'));
  } finally {
    loading.value = false;
  }
}

defineExpose({ reload });
onMounted(reload);
</script>

<template>
  <a-card :bordered="false" class="panel" title="最近执行日志（20 条）">
    <a-table
      row-key="id"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="{ pageSize: 10 }"
      :scroll="{ x: 900 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 'SUCCESS' ? 'green' : 'red'">{{ record.status }}</a-tag>
        </template>
        <template v-else-if="column.key === 'createdAt'">
          {{ String(record.createdAt).replace('T', ' ').slice(0, 19) }}
        </template>
      </template>
      <template #emptyText>
        <EmptyHint description="暂无执行记录" hint="任务按 cron 触发或点击「立即执行」后，结果会记录在这里" />
      </template>
    </a-table>
  </a-card>
</template>

<style scoped>
.panel {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
</style>
