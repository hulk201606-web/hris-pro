<script setup lang="ts">

/**
 * 管理端-日考勤明细页：按日期 / 员工筛选日考勤结果列表，
 * 支持手动触发重算（POST /att/daily/compute，需 att:daily:compute 权限）。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { SearchOutlined, SyncOutlined } from '@ant-design/icons-vue';
import { computeDaily, listDaily } from '../../../api/attendance';
import { errorText, resolveTagColor } from '../../../components/admin/crud-helpers';
import { useUserStore } from '../../../stores/user';
import type { AttQueryParams, DailyAttendance, DailyComputeResult } from '../../../types/admin';

const userStore = useUserStore();
const canCompute = computed(() => userStore.hasPerm('att:daily:compute'));

const statusLabels: Record<string, string> = {
  NORMAL: '正常',
  LATE: '迟到',
  EARLY: '早退',
  ABSENT: '缺勤',
  LEAVE: '请假',
};
const statusOptions = Object.entries(statusLabels).map(([value, label]) => ({ value, label }));

const columns = [
  { title: '员工 ID', dataIndex: 'empId', key: 'empId', width: 110 },
  { title: '工作日', dataIndex: 'workDate', key: 'workDate', width: 120 },
  { title: '出勤状态', dataIndex: 'attStatus', key: 'attStatus', width: 120 },
  { title: '上班打卡', dataIndex: 'punchIn', key: 'punchIn', width: 170 },
  { title: '下班打卡', dataIndex: 'punchOut', key: 'punchOut', width: 170 },
  { title: '请假(小时)', dataIndex: 'leaveHours', key: 'leaveHours', width: 110 },
  { title: '加班(小时)', dataIndex: 'overtimeHours', key: 'overtimeHours', width: 110 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 170 },
];

const filters = reactive<{ empIdText: string; from: string; to: string; attStatus?: string }>({
  empIdText: '',
  from: '',
  to: '',
  attStatus: undefined,
});
const rows = ref<DailyAttendance[]>([]);
const loading = ref(false);
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });

const computeForm = reactive<{ workDate: string }>({ workDate: '2026-10-16' });
const computing = ref(false);
const computeResult = ref<DailyComputeResult | null>(null);

function dt(v?: string): string {
  if (!v) return '—';
  return String(v).replace('T', ' ').slice(0, 16);
}

function statusLabel(v: string): string {
  return statusLabels[v] ?? v ?? '—';
}

async function load() {
  loading.value = true;
  try {
    const params: AttQueryParams = {};
    const empId = Number(filters.empIdText.trim());
    if (Number.isInteger(empId) && empId > 0) params.empId = empId;
    if (filters.from) params.from = filters.from;
    if (filters.to) params.to = filters.to;
    if (filters.attStatus) params.attStatus = filters.attStatus;
    const page = await listDaily(params, pagination.current, pagination.pageSize);
    rows.value = page.records;
    pagination.total = page.total;
  } catch (e) {
    message.error(errorText(e, '日考勤加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function handleQuery() {
  pagination.current = 1;
  load();
}

function onTableChange(pag: { current?: number; pageSize?: number }) {
  if (pag.current) pagination.current = pag.current;
  if (pag.pageSize) pagination.pageSize = pag.pageSize;
  load();
}

async function handleCompute() {
  if (!computeForm.workDate) {
    message.warning('请选择工作日');
    return;
  }
  computing.value = true;
  try {
    computeResult.value = await computeDaily(computeForm.workDate, []);
    message.success('日考勤计算完成');
    await load();
  } catch (e) {
    message.error(errorText(e, '日考勤计算失败'));
  } finally {
    computing.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="att-page">
    <section class="head">
      <div>
        <h2 class="head-title">日考勤</h2>
        <p class="head-sub">查询日考勤结果，支持按工作日触发重算（幂等 upsert，并生成异常单）。</p>
      </div>
      <a-space v-if="canCompute">
        <a-date-picker v-model:value="computeForm.workDate" value-format="YYYY-MM-DD" style="width: 150px" />
        <a-button type="primary" :loading="computing" @click="handleCompute">
          <template #icon><SyncOutlined /></template>
          重算当日
        </a-button>
      </a-space>
    </section>

    <a-card v-if="computeResult" :bordered="false" class="result-card">
      <a-descriptions :column="4" size="small" bordered>
        <a-descriptions-item label="工作日">{{ computeResult.workDate }}</a-descriptions-item>
        <a-descriptions-item label="员工数">{{ computeResult.employeeCount }}</a-descriptions-item>
        <a-descriptions-item label="正常">{{ computeResult.normalCount }}</a-descriptions-item>
        <a-descriptions-item label="迟到">{{ computeResult.lateCount }}</a-descriptions-item>
        <a-descriptions-item label="早退">{{ computeResult.earlyCount }}</a-descriptions-item>
        <a-descriptions-item label="缺勤">{{ computeResult.absentCount }}</a-descriptions-item>
        <a-descriptions-item label="请假">{{ computeResult.leaveCount }}</a-descriptions-item>
        <a-descriptions-item label="新增异常">{{ computeResult.newExceptionCount }}</a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-card :bordered="false" class="filter-card">
      <a-form layout="inline" :model="filters" class="filter-form">
        <a-form-item label="员工 ID">
          <a-input v-model:value="filters.empIdText" placeholder="可选" style="width: 130px" />
        </a-form-item>
        <a-form-item label="开始日期">
          <a-date-picker v-model:value="filters.from" value-format="YYYY-MM-DD" style="width: 140px" />
        </a-form-item>
        <a-form-item label="结束日期">
          <a-date-picker v-model:value="filters.to" value-format="YYYY-MM-DD" style="width: 140px" />
        </a-form-item>
        <a-form-item label="出勤状态">
          <a-select v-model:value="filters.attStatus" :options="statusOptions" allow-clear placeholder="全部" style="width: 130px" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleQuery">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card :bordered="false" class="table-card">
      <a-table
        row-key="id"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1100 }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'attStatus'">
            <a-tag :color="resolveTagColor(record.attStatus)">{{ statusLabel(record.attStatus) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'punchIn' || column.key === 'punchOut' || column.key === 'updatedAt'">
            {{ dt(record[column.key]) }}
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.att-page {
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

.result-card,
.filter-card,
.table-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.filter-form {
  row-gap: var(--space-3);
}
</style>
