<script setup lang="ts">

/**
 * 我的审批页：聚合跨流程待办任务列表（GET /flow/tasks/todo），
 * 支持同意 / 驳回操作（POST /flow/tasks/{id}/approve|reject）。
 * bizType 映射为中文业务名展示。
 */
import { onMounted, reactive, ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import { approveTask, fetchTodoTasks, rejectTask } from '../../api/flow';
import { BizError } from '../../api/error';
import type { TaskView } from '../../types/api';

const bizTypeLabels: Record<string, string> = {
  ATT_LEAVE: '请假申请',
  ATT_OT: '加班申请',
  ATT_TRIP: '出差申请',
  ATT_FIX: '补卡申请',
  GENERIC_APPROVAL: '通用审批',
  LEAVE_APPLY: '请假申请',
  OVERTIME_APPLY: '加班申请',
  PUNCH_FIX: '补卡申请',
};

const columns = [
  { title: '单号', dataIndex: 'bizKey', key: 'bizKey' },
  { title: '类型', dataIndex: 'bizType', key: 'bizType', width: 120 },
  { title: '当前节点', dataIndex: 'nodeName', key: 'nodeName' },
  { title: '发起时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 150 },
];

const records = ref<TaskView[]>([]);
const loading = ref(false);
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });

function bizTypeLabel(v: string): string {
  return bizTypeLabels[v] ?? v;
}

function formatDateTime(v?: string): string {
  if (!v) return '—';
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString('zh-CN', {
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

async function load() {
  loading.value = true;
  try {
    const page = await fetchTodoTasks(pagination.current, pagination.pageSize);
    records.value = page.records;
    pagination.total = page.total;
  } catch (err) {
    const msg = err instanceof BizError ? err.message : '待办加载失败';
    message.error(msg);
    records.value = [];
  } finally {
    loading.value = false;
  }
}

function onTableChange(pag: { current?: number; pageSize?: number }) {
  if (pag.current) pagination.current = pag.current;
  if (pag.pageSize) pagination.pageSize = pag.pageSize;
  load();
}

function doApprove(record: TaskView) {
  Modal.confirm({
    title: '审批通过',
    content: `确认通过「${record.bizKey}」？`,
    okText: '通过',
    cancelText: '取消',
    async onOk() {
      try {
        await approveTask(record.taskId as string, { comment: '同意' });
        message.success('已通过');
        await load();
      } catch (e) {
        message.error(e instanceof BizError ? e.message : '审批失败');
      }
    },
  });
}

function doReject(record: TaskView) {
  Modal.confirm({
    title: '审批驳回',
    content: `确认驳回「${record.bizKey}」？`,
    okText: '驳回',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await rejectTask(record.taskId as string, { comment: '驳回' });
        message.success('已驳回');
        await load();
      } catch (e) {
        message.error(e instanceof BizError ? e.message : '驳回失败');
      }
    },
  });
}

onMounted(load);
</script>

<template>
  <div class="my-approvals">
    <section class="head">
      <h2 class="head-title">我的审批</h2>
      <p class="head-sub">处理分配给我的待办事项</p>
    </section>

    <a-card :bordered="false" class="table-card">
      <a-table
        row-key="taskId"
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="pagination"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'bizType'">
            {{ bizTypeLabel(record.bizType) }}
          </template>
          <template v-else-if="column.key === 'createTime'">
            {{ formatDateTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="record.status === 'TODO'">
              <a-button type="link" size="small" @click="doApprove(record)">通过</a-button>
              <a-button type="link" size="small" danger @click="doReject(record)">驳回</a-button>
            </a-space>
            <span v-else>—</span>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.my-approvals {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.head-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: var(--ls-heading);
  color: var(--color-text-primary);
}

.head-sub {
  margin: var(--space-2) 0 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.table-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
</style>
