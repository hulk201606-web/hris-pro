<script setup lang="ts">
/**
 * 管理端-外出/居家办公审批（ATT-F-018，att:out-office:read/write）。
 * 按员工/状态过滤分页列表，行内提供通过/驳回（仅 PROCESSING 可审，由后端校验）。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { approveOutOffice, listOutOffices, rejectOutOffice } from '../../../api/out-office';
import type { OutOfficeItem } from '../../../api/out-office';
import { useUserStore } from '../../../stores/user';

const TYPE_MAP: Record<string, string> = { OUT: '外出', WFH: '居家办公' };
const STATUS_OPTIONS = [
  { label: '待审批', value: 'PROCESSING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
];

const userStore = useUserStore();
const canAudit = computed(() => userStore.hasPerm('att:out-office:write'));

const filters = reactive({ empId: undefined as number | undefined, status: undefined as string | undefined });
const rows = ref<OutOfficeItem[]>([]);
const loading = ref(false);
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });

const tableColumns = [
  { title: '员工 ID', dataIndex: 'empId', key: 'empId', width: 100 },
  { title: '类型', key: 'type', width: 100 },
  { title: '开始时间', key: 'startTime', width: 160 },
  { title: '结束时间', key: 'endTime', width: 160 },
  { title: '事由', key: 'reason' },
  { title: '状态', key: 'status', width: 110 },
  { title: '审批人', dataIndex: 'approverId', key: 'approverId', width: 100 },
  { title: '操作', key: 'action', width: 150, fixed: 'right' as const },
];

async function load() {
  loading.value = true;
  try {
    const page = await listOutOffices({
      empId: filters.empId,
      status: filters.status,
      page: pagination.current,
      size: pagination.pageSize,
    });
    rows.value = page.records ?? [];
    pagination.total = page.total ?? 0;
  } catch (e) {
    message.error(errorText(e, '申请列表加载失败'));
    rows.value = [];
    pagination.total = 0;
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  pagination.current = 1;
  load();
}

function onPageChange(p: number, s: number) {
  pagination.current = p;
  pagination.pageSize = s;
  load();
}

async function audit(record: OutOfficeItem, action: 'approve' | 'reject') {
  const isApprove = action === 'approve';
  try {
    await (isApprove ? approveOutOffice(record.id) : rejectOutOffice(record.id));
    message.success(isApprove ? '已通过' : '已驳回');
    await load();
  } catch (e) {
    message.error(errorText(e, isApprove ? '审批通过失败' : '审批驳回失败'));
  }
}

function typeText(value: string): string {
  return TYPE_MAP[value] ?? value;
}

function statusColor(status: string): string {
  if (status === 'APPROVED') return 'green';
  if (status === 'REJECTED') return 'red';
  return 'blue';
}

function timeText(value: string): string {
  return String(value || '').replace('T', ' ').slice(0, 16);
}

onMounted(load);
</script>

<template>
  <div class="out-office-admin">
    <section class="head">
      <div>
        <h2 class="head-title">外出 / 居家审批</h2>
        <p class="head-sub">审核员工提交的外出与居家办公申请，仅待审批状态可操作</p>
      </div>
      <a-button @click="load">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </section>

    <a-card :bordered="false" class="panel">
      <a-space wrap class="filters">
        <a-input-number v-model:value="filters.empId" :min="1" placeholder="员工 ID（选填）" style="width: 160px" />
        <a-select v-model:value="filters.status" :options="STATUS_OPTIONS" allow-clear placeholder="状态（选填）" style="width: 140px" />
        <a-button type="primary" @click="onSearch">查询</a-button>
      </a-space>

      <a-table
        row-key="id"
        :columns="tableColumns"
        :data-source="rows"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1000 }"
        @change="onPageChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <a-tag :color="record.type === 'OUT' ? 'orange' : 'cyan'">{{ typeText(record.type) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'startTime' || column.key === 'endTime'">
            {{ timeText(record[column.key]) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="canAudit">
              <a-popconfirm title="确认通过该申请？" @confirm="audit(record, 'approve')">
                <a-button type="link" size="small">通过</a-button>
              </a-popconfirm>
              <a-popconfirm title="确认驳回该申请？" @confirm="audit(record, 'reject')">
                <a-button type="link" size="small" danger>驳回</a-button>
              </a-popconfirm>
            </a-space>
            <span v-else>—</span>
          </template>
          <template v-else>{{ record[column.key] ?? '—' }}</template>
        </template>
        <template #emptyText>
          <EmptyHint description="暂无申请记录" hint="调整过滤条件后点击「查询」" />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.out-office-admin {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
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

.panel {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.filters {
  margin-bottom: var(--space-4);
}
</style>
