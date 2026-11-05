<script setup lang="ts">

/**
 * 日考勤月结锁（批 11#19）：期间冻结后该月任意日期不可重算，差异只能走月结调整。
 * 列表 GET /att/daily-close（active=true 只看生效锁）；
 * 月结 POST /att/daily-close/{period}/lock；解锁 POST /att/daily-close/{period}/unlock（reason 必填）。
 * 权限点：读 att:group:read，写 att:closing:run（后端 DailyCloseController 复用既有编码）。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { listDailyCloseLocks, lockPeriod, unlockPeriod } from '../../../api/attendance-close';
import { useUserStore } from '../../../stores/user';
import type { DailyCloseLock } from '../../../types/api';

const userStore = useUserStore();
const canRun = computed(() => userStore.hasPerm('att:closing:run'));

const rows = ref<DailyCloseLock[]>([]);
const loading = ref(false);
const activeOnly = ref(true);

const lockOpen = ref(false);
const lockSubmitting = ref(false);
const lockForm = reactive<{ period: string }>({ period: '' });

const unlockOpen = ref(false);
const unlockSubmitting = ref(false);
const unlockForm = reactive<{ period: string; reason: string }>({ period: '', reason: '' });

function statusText(status: string): string {
  return status === 'ACTIVE' ? '锁定中' : '已解锁';
}

function fmtTime(v: string | null): string {
  return v ? v.replace('T', ' ').slice(0, 19) : '—';
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listDailyCloseLocks(activeOnly.value ? true : undefined);
  } catch (e) {
    message.error(errorText(e, '月结锁列表加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function openLock() {
  const now = new Date();
  lockForm.period = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  lockOpen.value = true;
}

async function submitLock() {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(lockForm.period)) {
    message.warning('请选择月结期间（YYYY-MM）');
    return;
  }
  lockSubmitting.value = true;
  try {
    await lockPeriod(lockForm.period);
    message.success(`期间 ${lockForm.period} 已月结锁定`);
    lockOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, '月结失败'));
  } finally {
    lockSubmitting.value = false;
  }
}

function openUnlock(record: DailyCloseLock) {
  unlockForm.period = record.period;
  unlockForm.reason = '';
  unlockOpen.value = true;
}

async function submitUnlock() {
  if (!unlockForm.reason.trim()) {
    message.warning('解锁理由必填，用于审计留痕');
    return;
  }
  unlockSubmitting.value = true;
  try {
    await unlockPeriod(unlockForm.period, unlockForm.reason.trim());
    message.success(`期间 ${unlockForm.period} 已解锁`);
    unlockOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, '解锁失败'));
  } finally {
    unlockSubmitting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="close-page">
    <section class="head">
      <div>
        <h2 class="head-title">月结锁管理</h2>
        <p class="head-sub">锁定期间后该月日考勤不可重算，差异需走月结调整通道；解锁必须填写理由</p>
      </div>
      <a-space>
        <a-switch v-model:checked="activeOnly" checked-children="仅生效锁" un-checked-children="全部" @change="load" />
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canRun" type="primary" @click="openLock">月结</a-button>
      </a-space>
    </section>

    <a-card :bordered="false" class="card">
      <a-table
        row-key="id"
        :columns="[
          { title: '期间', dataIndex: 'period', key: 'period', width: 120 },
          { title: '状态', key: 'status', width: 110 },
          { title: '锁定时间', dataIndex: 'closedAt', key: 'closedAt', width: 180 },
          { title: '锁定人', dataIndex: 'closedBy', key: 'closedBy', width: 110 },
          { title: '解锁时间', dataIndex: 'releasedAt', key: 'releasedAt', width: 180 },
          { title: '解锁理由', dataIndex: 'releaseReason', key: 'releaseReason' },
          { title: '操作', key: 'action', width: 120, fixed: 'right' },
        ]"
        :data-source="rows"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        :scroll="{ x: 960 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'red' : 'default'">{{ statusText(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'closedAt'">{{ fmtTime(record.closedAt) }}</template>
          <template v-else-if="column.key === 'releasedAt'">{{ fmtTime(record.releasedAt) }}</template>
          <template v-else-if="column.key === 'closedBy'">{{ record.closedBy ?? '—' }}</template>
          <template v-else-if="column.key === 'releaseReason'">{{ record.releaseReason || '—' }}</template>
          <template v-else-if="column.key === 'action'">
            <a-button
              v-if="canRun && record.status === 'ACTIVE'"
              type="link"
              size="small"
              danger
              @click="openUnlock(record)"
            >
              解锁
            </a-button>
            <span v-else class="muted">—</span>
          </template>
        </template>
        <template #emptyText>
          <EmptyHint description="暂无月结锁记录" :hint="canRun ? '点击右上角「月结」锁定已核算完成的期间' : ''" />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="lockOpen"
      title="期间月结"
      :confirm-loading="lockSubmitting"
      :mask-closable="false"
      ok-text="确认锁定"
      cancel-text="取消"
      @ok="submitLock"
    >
      <a-form :model="lockForm" layout="vertical">
        <a-form-item label="月结期间" required>
          <a-date-picker v-model:value="lockForm.period" picker="month" value-format="YYYY-MM" style="width: 100%" />
        </a-form-item>
        <a-alert type="info" show-icon message="锁定后该期间日考勤重算将被拒绝，请确认已全部核算完毕。" />
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="unlockOpen"
      title="解锁期间"
      :confirm-loading="unlockSubmitting"
      :mask-closable="false"
      ok-text="确认解锁"
      cancel-text="取消"
      @ok="submitUnlock"
    >
      <a-form :model="unlockForm" layout="vertical">
        <a-form-item label="期间">
          <a-input :value="unlockForm.period" disabled />
        </a-form-item>
        <a-form-item label="解锁理由" required>
          <a-textarea v-model:value="unlockForm.reason" :rows="3" placeholder="请说明解锁原因，将写入审计日志" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.close-page {
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

.card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.muted {
  color: var(--color-text-muted);
}
</style>
