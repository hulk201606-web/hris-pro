<script setup lang="ts">
/**
 * 管理端-期间关账页：ACTIVE 锁列表 + 关账弹窗（输入 periodCode）+
 * 解锁弹窗（reason 必填，落审计）。期间被锁后 run 的 submit/update 被拒。
 * 走 /cmp/period-locks 契约，权限复用 cmp:payroll-run:read/write（后端不新增权限点）。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { LockOutlined, SearchOutlined, UnlockOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { listPeriodLocks, lockPeriod, unlockPeriod } from '../../../api/compensation';
import { useUserStore } from '../../../stores/user';
import type { PeriodLock } from '../../../types/api-batch2';

const userStore = useUserStore();
const canRead = computed(() => userStore.hasPerm('cmp:payroll-run:read'));
const canWrite = computed(() => userStore.hasPerm('cmp:payroll-run:write'));

const columns = [
  { title: '期间', dataIndex: 'periodCode', key: 'periodCode', width: 130 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '关账人', dataIndex: 'lockedBy', key: 'lockedBy', width: 100 },
  { title: '关账时间', dataIndex: 'lockedAt', key: 'lockedAt', width: 170 },
  { title: '解锁人', dataIndex: 'releasedBy', key: 'releasedBy', width: 100 },
  { title: '解锁时间', dataIndex: 'releasedAt', key: 'releasedAt', width: 170 },
  { title: '解锁原因', dataIndex: 'unlockReason', key: 'unlockReason', ellipsis: true },
];

const filters = reactive<{ activeOnly: boolean; periodCode: string }>({
  activeOnly: true,
  periodCode: '',
});
const rows = ref<PeriodLock[]>([]);
const loading = ref(false);
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });

const lockOpen = ref(false);
const lockPeriodCode = ref('');
const lockSubmitting = ref(false);

const unlockOpen = ref(false);
const unlockRecord = ref<PeriodLock | null>(null);
const unlockReason = ref('');
const unlockSubmitting = ref(false);

const tableColumns = computed<any[]>(() => {
  const cols: any[] = [...columns];
  if (canWrite.value) cols.push({ title: '操作', key: 'action', width: 110, fixed: 'right' });
  return cols;
});

function dt(v?: string | null): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—';
}

async function load() {
  if (!canRead.value) return;
  loading.value = true;
  try {
    const query: { active?: boolean; periodCode?: string } = {};
    if (filters.activeOnly) query.active = true;
    if (filters.periodCode.trim()) query.periodCode = filters.periodCode.trim();
    const page = await listPeriodLocks(query, pagination.current, pagination.pageSize);
    rows.value = page.records;
    pagination.total = page.total;
  } catch (e) {
    message.error(errorText(e, '关账记录加载失败'));
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

function openLock() {
  lockPeriodCode.value = '';
  lockOpen.value = true;
}

async function handleLock() {
  const periodCode = lockPeriodCode.value.trim();
  if (!periodCode) {
    message.warning('请输入期间编码（如 2026-10）');
    return;
  }
  lockSubmitting.value = true;
  try {
    await lockPeriod(periodCode);
    message.success(`期间 ${periodCode} 已关账`);
    lockOpen.value = false;
    filters.activeOnly = true;
    pagination.current = 1;
    await load();
  } catch (e) {
    message.error(errorText(e, '关账失败'));
  } finally {
    lockSubmitting.value = false;
  }
}

function openUnlock(record: PeriodLock) {
  unlockRecord.value = record;
  unlockReason.value = '';
  unlockOpen.value = true;
}

async function handleUnlock() {
  const reason = unlockReason.value.trim();
  if (reason.length < 4) {
    message.warning('解锁原因至少 4 个字符');
    return;
  }
  if (!unlockRecord.value) return;
  unlockSubmitting.value = true;
  try {
    await unlockPeriod(unlockRecord.value.periodCode, reason);
    message.success(`期间 ${unlockRecord.value.periodCode} 已解锁`);
    unlockOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, '解锁失败'));
  } finally {
    unlockSubmitting.value = false;
  }
}

function confirmUnlock(record: PeriodLock) {
  Modal.confirm({
    title: '确认解锁',
    content: `解锁后该期间恢复薪酬批次提交/更新，确定解锁 ${record.periodCode} 吗？`,
    okText: '下一步',
    cancelText: '取消',
    onOk() {
      openUnlock(record);
    },
  });
}

onMounted(load);
</script>

<template>
  <div class="lock-page">
    <section class="head">
      <div>
        <h2 class="head-title">期间关账</h2>
        <p class="head-sub">期间关账后，该期间薪酬批次的提交与更新将被拒绝；解锁须填写原因并落审计</p>
      </div>
      <a-button v-if="canWrite" type="primary" @click="openLock">
        <template #icon><LockOutlined /></template>
        关账
      </a-button>
    </section>

    <a-card :bordered="false" class="filter-card">
      <a-form layout="inline" class="filter-form">
        <a-form-item label="仅看锁定中">
          <a-switch v-model:checked="filters.activeOnly" />
        </a-form-item>
        <a-form-item label="期间编码">
          <a-input v-model:value="filters.periodCode" placeholder="如 2026-10" style="width: 150px" allow-clear />
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
        :scroll="{ x: 980 }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'red' : 'default'">
              {{ record.status === 'ACTIVE' ? '已关账' : '已解锁' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'lockedAt' || column.key === 'releasedAt'">
            {{ dt(record[column.key]) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button
              v-if="canWrite && record.status === 'ACTIVE'"
              type="link"
              size="small"
              danger
              @click="confirmUnlock(record)"
            >
              <template #icon><UnlockOutlined /></template>
              解锁
            </a-button>
            <span v-else>—</span>
          </template>
        </template>
        <template #emptyText>
          <EmptyHint description="暂无关账记录" :hint="canWrite ? '点击右上角「关账」锁定薪酬期间' : ''" />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="lockOpen"
      title="期间关账"
      :confirm-loading="lockSubmitting"
      :mask-closable="false"
      ok-text="确认关账"
      cancel-text="取消"
      @ok="handleLock"
    >
      <p class="lock-tip">关账后该期间的薪酬批次不可提交/更新（提示「期间已关账」），请确认核算已完成。</p>
      <a-form layout="vertical">
        <a-form-item label="期间编码" required>
          <a-input v-model:value="lockPeriodCode" placeholder="如 2026-10" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="unlockOpen"
      :title="`解锁 ${unlockRecord?.periodCode ?? ''}`"
      :confirm-loading="unlockSubmitting"
      :mask-closable="false"
      ok-text="确认解锁"
      cancel-text="取消"
      @ok="handleUnlock"
    >
      <a-form layout="vertical">
        <a-form-item label="解锁原因" required>
          <a-textarea v-model:value="unlockReason" :rows="3" placeholder="说明解锁原因（至少 4 字），将落审计日志" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.lock-page {
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

.lock-tip {
  margin: 0 0 12px;
  color: var(--color-text-secondary);
}
</style>
