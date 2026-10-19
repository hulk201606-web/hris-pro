<script setup lang="ts">

/**
 * 管理端-考勤异常工作台：迟到 / 早退 / 缺卡等异常列表，
 * 支持单条或批量认定（RESOLVE）/ 豁免（IGNORE），需 att:exception:handle 权限。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { SearchOutlined } from '@ant-design/icons-vue';
import { batchHandleExceptions, listExceptions } from '../../../api/attendance';
import { errorText, resolveTagColor } from '../../../components/admin/crud-helpers';
import { useUserStore } from '../../../stores/user';
import type { AttException, AttQueryParams } from '../../../types/admin';

const userStore = useUserStore();
const canHandle = computed(() => userStore.hasPerm('att:exception:handle'));

const typeLabels: Record<string, string> = {
  LATE: '迟到',
  EARLY: '早退',
  MISSING_PUNCH: '漏打卡',
  ABSENT: '缺勤',
};
const statusLabels: Record<string, string> = {
  OPEN: '待处理',
  RESOLVED: '已认定',
  IGNORED: '已豁免',
};
const typeOptions = Object.entries(typeLabels).map(([value, label]) => ({ value, label }));
const statusOptions = Object.entries(statusLabels).map(([value, label]) => ({ value, label }));

const columns = [
  { title: '员工 ID', dataIndex: 'empId', key: 'empId', width: 110 },
  { title: '工作日', dataIndex: 'workDate', key: 'workDate', width: 120 },
  { title: '异常类型', dataIndex: 'exceptionType', key: 'exceptionType', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '异常说明', dataIndex: 'description', key: 'description' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 150, fixed: 'right' },
];

const filters = reactive<{
  empIdText: string;
  from: string;
  to: string;
  exceptionType?: string;
  status?: string;
}>({ empIdText: '', from: '', to: '', exceptionType: undefined, status: undefined });

const rows = ref<AttException[]>([]);
const loading = ref(false);
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });
const selectedRowKeys = ref<number[]>([]);
const handling = ref(false);

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: (string | number)[]) => {
    selectedRowKeys.value = keys.map((k) => Number(k));
  },
}));

function dt(v?: string): string {
  if (!v) return '—';
  return String(v).replace('T', ' ').slice(0, 16);
}

async function load() {
  loading.value = true;
  try {
    const params: AttQueryParams = {};
    const empId = Number(filters.empIdText.trim());
    if (Number.isInteger(empId) && empId > 0) params.empId = empId;
    if (filters.from) params.from = filters.from;
    if (filters.to) params.to = filters.to;
    if (filters.exceptionType) params.exceptionType = filters.exceptionType;
    if (filters.status) params.status = filters.status;
    const page = await listExceptions(params, pagination.current, pagination.pageSize);
    rows.value = page.records;
    pagination.total = page.total;
  } catch (e) {
    message.error(errorText(e, '异常列表加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function handleQuery() {
  pagination.current = 1;
  selectedRowKeys.value = [];
  load();
}

function onTableChange(pag: { current?: number; pageSize?: number }) {
  if (pag.current) pagination.current = pag.current;
  if (pag.pageSize) pagination.pageSize = pag.pageSize;
  load();
}

async function doHandle(ids: number[], action: 'RESOLVE' | 'IGNORE') {
  if (!ids.length) {
    message.warning('请先选择异常单');
    return;
  }
  handling.value = true;
  try {
    const result = await batchHandleExceptions(ids, action);
    const actionText = action === 'IGNORE' ? '豁免' : '认定';
    message.success(`已${actionText} ${result.success} 条${result.failedIds.length ? `，失败 ${result.failedIds.length} 条` : ''}`);
    selectedRowKeys.value = [];
    await load();
  } catch (e) {
    message.error(errorText(e, '异常处理失败'));
  } finally {
    handling.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="att-page">
    <section class="head">
      <div>
        <h2 class="head-title">异常工作台</h2>
        <p class="head-sub">处理迟到/早退/漏打卡/缺勤异常：认定（销异常，保留事实）或豁免（不纳入月结考核）。</p>
      </div>
    </section>

    <a-card :bordered="false" class="filter-card">
      <a-form layout="inline" :model="filters" class="filter-form">
        <a-form-item label="员工 ID">
          <a-input v-model:value="filters.empIdText" placeholder="可选" style="width: 120px" />
        </a-form-item>
        <a-form-item label="开始日期">
          <a-date-picker v-model:value="filters.from" value-format="YYYY-MM-DD" style="width: 140px" />
        </a-form-item>
        <a-form-item label="结束日期">
          <a-date-picker v-model:value="filters.to" value-format="YYYY-MM-DD" style="width: 140px" />
        </a-form-item>
        <a-form-item label="异常类型">
          <a-select v-model:value="filters.exceptionType" :options="typeOptions" allow-clear placeholder="全部" style="width: 130px" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="filters.status" :options="statusOptions" allow-clear placeholder="全部" style="width: 120px" />
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
      <div v-if="canHandle" class="toolbar">
        <span class="toolbar-hint">已选 {{ selectedRowKeys.length }} 条</span>
        <a-space>
          <a-button size="small" :loading="handling" :disabled="!selectedRowKeys.length" @click="doHandle(selectedRowKeys, 'RESOLVE')">
            批量认定
          </a-button>
          <a-button size="small" :loading="handling" :disabled="!selectedRowKeys.length" @click="doHandle(selectedRowKeys, 'IGNORE')">
            批量豁免
          </a-button>
        </a-space>
      </div>

      <a-table
        row-key="id"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="pagination"
        :row-selection="canHandle ? rowSelection : undefined"
        :scroll="{ x: 1000 }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'exceptionType'">
            <a-tag :color="resolveTagColor(record.exceptionType)">{{ typeLabels[record.exceptionType] ?? record.exceptionType }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="resolveTagColor(record.status)">{{ statusLabels[record.status] ?? record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ dt(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="canHandle && record.status === 'OPEN'">
              <a-button type="link" size="small" :loading="handling" @click="doHandle([record.id], 'RESOLVE')">认定</a-button>
              <a-button type="link" size="small" :loading="handling" @click="doHandle([record.id], 'IGNORE')">豁免</a-button>
            </a-space>
            <span v-else>—</span>
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

.filter-card,
.table-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.filter-form {
  row-gap: var(--space-3);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}

.toolbar-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>
