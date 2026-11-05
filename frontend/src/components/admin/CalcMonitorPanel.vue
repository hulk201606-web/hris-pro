<script setup lang="ts">
/**
 * 算薪监控的进度与任务展示区：进度条 + 汇总数字 + 逐员工任务表
 * （SUCCESS 绿 / FAILED 红 / SKIPPED 灰；FAILED/SKIPPED 行提供单人重算按钮）。
 */
import { computed } from 'vue';
import { RedoOutlined } from '@ant-design/icons-vue';
import EmptyHint from './EmptyHint.vue';
import type { CalcProgress, CalcTask } from '../../types/api-batch3';

const props = defineProps<{
  progress: CalcProgress | null;
  tasks: CalcTask[];
  loading: boolean;
  canWrite: boolean;
  hasRunSelected: boolean;
  retrying: Record<number, boolean>;
}>();

const emit = defineEmits<{ (e: 'retry', task: CalcTask): void }>();

const STATUS_COLORS: Record<string, string> = {
  SUCCESS: 'green',
  FAILED: 'red',
  SKIPPED: 'default',
};
const STATUS_LABELS: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  SKIPPED: '跳过',
};

const columns = [
  { title: '员工 ID', dataIndex: 'empId', key: 'empId', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '错误信息', dataIndex: 'error', key: 'error', ellipsis: true },
  { title: '任务时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 160 },
];

const tableColumns = computed<any[]>(() => {
  const cols: any[] = columns.map((c) => ({ ...c }));
  if (props.canWrite) cols.push({ title: '操作', key: 'action', width: 110, fixed: 'right' });
  return cols;
});

function dt(v?: string | null): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—';
}
</script>

<template>
  <a-card v-if="progress" :bordered="false" class="progress-card">
    <a-row :gutter="16" align="middle">
      <a-col :span="10">
        <a-progress
          :percent="progress.progressPercent"
          :status="progress.failed > 0 && progress.progressPercent >= 100 ? 'exception' : 'normal'"
        />
      </a-col>
      <a-col :span="14">
        <a-space :size="24">
          <span>总人数 <b>{{ progress.total }}</b></span>
          <span>成功 <b class="text-success">{{ progress.success }}</b></span>
          <span>失败 <b class="text-danger">{{ progress.failed }}</b></span>
          <span>跳过 <b>{{ progress.skipped }}</b></span>
        </a-space>
      </a-col>
    </a-row>
  </a-card>

  <a-card :bordered="false" class="table-card">
    <a-table
      row-key="id"
      :columns="tableColumns"
      :data-source="tasks"
      :loading="loading"
      :pagination="false"
      :scroll="{ x: 800 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="STATUS_COLORS[record.status] || 'default'">
            {{ STATUS_LABELS[record.status] || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'updatedAt'">
          {{ dt(record.updatedAt) }}
        </template>
        <template v-else-if="column.key === 'error'">
          {{ record.error || '—' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button
            v-if="canWrite && (record.status === 'FAILED' || record.status === 'SKIPPED')"
            type="link"
            size="small"
            :loading="retrying[record.id]"
            @click="emit('retry', record)"
          >
            <template #icon><RedoOutlined /></template>
            单人重算
          </a-button>
          <span v-else>—</span>
        </template>
      </template>
      <template #emptyText>
        <EmptyHint
          description="暂无算薪任务"
          :hint="hasRunSelected ? '该批次尚未执行计算，点击右上角「执行计算」开始' : '请先在上方选择核算批次'"
        />
      </template>
    </a-table>
  </a-card>
</template>

<style scoped>
.progress-card,
.table-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.text-success {
  color: var(--color-success, #52c41a);
}

.text-danger {
  color: var(--color-danger, #ff4d4f);
}
</style>
