<script setup lang="ts">
/**
 * 管理端-面谈记录页：按周期/员工过滤分页列表，
 * 新建/编辑走 InterviewFormModal（PIP 起止日期联动），
 * PIP 进行中的记录可「登记结果」（PASSED/FAILED）。
 * 走 /prf/interviews 契约，权限 prf:interview:read/write。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import InterviewFormModal from '../../../components/admin/performance/InterviewFormModal.vue';
import { errorText, resolveTagColor } from '../../../components/admin/crud-helpers';
import { listInterviews, submitPipResult } from '../../../api/performance';
import { useUserStore } from '../../../stores/user';
import type { InterviewRecord, PipStatus } from '../../../types/api-batch2';

const userStore = useUserStore();
const canRead = computed(() => userStore.hasPerm('prf:interview:read'));
const canWrite = computed(() => userStore.hasPerm('prf:interview:write'));

const conclusionLabels: Record<string, string> = {
  IMPROVE: '改进',
  KEEP: '维持',
  PROMOTE: '晋升',
  PIP: '绩效改进',
};
const pipStatusLabels: Record<string, string> = {
  ONGOING: '进行中',
  PASSED: '通过',
  FAILED: '未通过',
};

const columns = [
  { title: '周期', dataIndex: 'cycleId', key: 'cycleId', width: 90 },
  { title: '员工', dataIndex: 'empId', key: 'empId', width: 90 },
  { title: '轮次', dataIndex: 'round', key: 'round', width: 80 },
  { title: '面谈日期', dataIndex: 'interviewDate', key: 'interviewDate', width: 120 },
  { title: '面谈人', dataIndex: 'interviewerEmpId', key: 'interviewerEmpId', width: 100 },
  { title: '结论', dataIndex: 'conclusion', key: 'conclusion', width: 110 },
  { title: 'PIP 起止', key: 'pipSpan', width: 200 },
  { title: 'PIP 状态', dataIndex: 'pipStatus', key: 'pipStatus', width: 100 },
  { title: '纪要', dataIndex: 'summary', key: 'summary', ellipsis: true },
];

const tableColumns = computed<any[]>(() => {
  const cols: any[] = [...columns];
  if (canWrite.value) cols.push({ title: '操作', key: 'action', width: 170, fixed: 'right' });
  return cols;
});

const filters = reactive<{ cycleIdText: string; empIdText: string }>({ cycleIdText: '', empIdText: '' });
const rows = ref<InterviewRecord[]>([]);
const loading = ref(false);
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });

const modalOpen = ref(false);
const modalMode = ref<'create' | 'edit'>('create');
const editingRecord = ref<InterviewRecord | null>(null);

const pipResultOpen = ref(false);
const pipRecord = ref<InterviewRecord | null>(null);
const pipStatus = ref<PipStatus | undefined>(undefined);
const pipSubmitting = ref(false);

function pipSpan(record: InterviewRecord): string {
  if (record.conclusion !== 'PIP') return '—';
  return `${record.pipStartDate || '—'} ~ ${record.pipEndDate || '—'}`;
}

function pipOngoing(record: InterviewRecord): boolean {
  return record.conclusion === 'PIP' && record.pipStatus === 'ONGOING';
}

async function load() {
  if (!canRead.value) return;
  loading.value = true;
  try {
    const query: { cycleId?: number; empId?: number } = {};
    const cycleId = Number(filters.cycleIdText.trim());
    const empId = Number(filters.empIdText.trim());
    if (Number.isInteger(cycleId) && cycleId > 0) query.cycleId = cycleId;
    if (Number.isInteger(empId) && empId > 0) query.empId = empId;
    const page = await listInterviews(query, pagination.current, pagination.pageSize);
    rows.value = page.records;
    pagination.total = page.total;
  } catch (e) {
    message.error(errorText(e, '面谈记录加载失败'));
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

function openCreate() {
  modalMode.value = 'create';
  editingRecord.value = null;
  modalOpen.value = true;
}

function openEdit(record: InterviewRecord) {
  modalMode.value = 'edit';
  editingRecord.value = record;
  modalOpen.value = true;
}

async function handleSaved() {
  pagination.current = 1;
  await load();
}

function openPipResult(record: InterviewRecord) {
  pipRecord.value = record;
  pipStatus.value = undefined;
  pipResultOpen.value = true;
}

async function handlePipResult() {
  if (!pipStatus.value) {
    message.warning('请选择 PIP 结果');
    return;
  }
  if (!pipRecord.value) return;
  pipSubmitting.value = true;
  try {
    await submitPipResult(pipRecord.value.id, pipStatus.value);
    message.success('PIP 结果已登记');
    pipResultOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, 'PIP 结果登记失败'));
  } finally {
    pipSubmitting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="interview-page">
    <section class="head">
      <div>
        <h2 class="head-title">面谈记录</h2>
        <p class="head-sub">绩效面谈纪要与结论归档；结论为绩效改进时须约定 PIP 起止日期并跟进结果</p>
      </div>
      <a-button v-if="canWrite" type="primary" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        新建
      </a-button>
    </section>

    <a-card :bordered="false" class="filter-card">
      <a-form layout="inline" class="filter-form">
        <a-form-item label="周期 ID">
          <a-input v-model:value="filters.cycleIdText" placeholder="可选" style="width: 130px" />
        </a-form-item>
        <a-form-item label="员工 ID">
          <a-input v-model:value="filters.empIdText" placeholder="可选" style="width: 130px" />
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
        :columns="tableColumns"
        :data-source="rows"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1000 }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button v-if="canWrite" type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-button v-if="canWrite && pipOngoing(record)" type="link" size="small" @click="openPipResult(record)">
                登记结果
              </a-button>
            </a-space>
          </template>
          <template v-else-if="column.key === 'conclusion'">
            <a-tag :color="resolveTagColor(record.conclusion)">
              {{ conclusionLabels[record.conclusion] ?? record.conclusion }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'pipSpan'">{{ pipSpan(record) }}</template>
          <template v-else-if="column.key === 'pipStatus'">
            <a-tag v-if="record.pipStatus" :color="record.pipStatus === 'PASSED' ? 'green' : record.pipStatus === 'FAILED' ? 'red' : 'gold'">
              {{ pipStatusLabels[record.pipStatus] ?? record.pipStatus }}
            </a-tag>
            <span v-else>—</span>
          </template>
        </template>
        <template #emptyText>
          <EmptyHint description="暂无面谈记录" :hint="canWrite ? '点击右上角「新建」添加第一条记录' : ''" />
        </template>
      </a-table>
    </a-card>

    <InterviewFormModal
      v-model:open="modalOpen"
      :mode="modalMode"
      :record="editingRecord"
      @saved="handleSaved"
    />

    <a-modal
      v-model:open="pipResultOpen"
      title="登记 PIP 结果"
      :confirm-loading="pipSubmitting"
      ok-text="提交"
      cancel-text="取消"
      @ok="handlePipResult"
    >
      <p class="pip-emp">员工 ID：{{ pipRecord?.empId }}（PIP：{{ pipRecord ? pipSpan(pipRecord) : '—' }}）</p>
      <a-select
        v-model:value="pipStatus"
        style="width: 100%"
        placeholder="请选择 PIP 结果"
        :options="[{ value: 'PASSED', label: '通过' }, { value: 'FAILED', label: '未通过' }]"
      />
    </a-modal>
  </div>
</template>

<style scoped>
.interview-page {
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

.filter-card,
.table-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.filter-form {
  row-gap: var(--space-3);
}

.pip-emp {
  margin: 0 0 12px;
  color: var(--color-text-secondary);
}
</style>
