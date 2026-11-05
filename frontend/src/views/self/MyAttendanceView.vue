<script setup lang="ts">

/**
 * 我的考勤（自助，批 11#20）：
 * - 今日卡片：GET /att/daily/today（班次 / 上下班打卡 / 打卡状态）；
 * - 假勤台账：GET /att/ledgers 分页查询（按假种 / 时间区间过滤）。
 * 两个端点均按当前登录员工过滤，前端不再自行拼装多域请求。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../components/admin/EmptyHint.vue';
import { fetchAttendanceLedgers, fetchTodayAttendance } from '../../api/self';
import { listLeaveTypes } from '../../api/attendance';
import { BizError } from '../../api/error';
import { useUserStore } from '../../stores/user';
import type { AttLedgerRow, AttPunchToday } from '../../types/api';

const userStore = useUserStore();
const empId = computed(() => userStore.employeeId);

/** 打卡状态文案（后端 PunchService.today） */
const punchStatusLabels: Record<string, string> = {
  NOT_PUNCHED: '未打卡',
  PUNCHED_IN: '已上班打卡',
  PUNCHED_OUT: '已完成下班打卡',
};
const punchStatusColor: Record<string, string> = {
  NOT_PUNCHED: 'default',
  PUNCHED_IN: 'processing',
  PUNCHED_OUT: 'green',
};

const today = ref<AttPunchToday | null>(null);
const todayLoading = ref(true);

const rows = ref<AttLedgerRow[]>([]);
const loading = ref(false);
const total = ref(0);
const pagination = reactive({ current: 1, pageSize: 10 });
const leaveTypeOptions = ref<{ label: string; value: number }[]>([]);
const filters = reactive<{ leaveTypeId?: number; range: [string, string] | null }>({
  leaveTypeId: undefined,
  range: null,
});

const todayStatusText = computed(() =>
  today.value?.punchStatus ? punchStatusLabels[today.value.punchStatus] ?? today.value.punchStatus : '—',
);
const todayStatusTag = computed(() =>
  today.value?.punchStatus ? punchStatusColor[today.value.punchStatus] ?? 'default' : 'default',
);

function fmtTime(v: string | null): string {
  return v ? v.replace('T', ' ').slice(0, 19) : '—';
}

function fmtAmount(v: unknown): string {
  const n = Number(v);
  return Number.isFinite(n) ? n.toFixed(2) : '—';
}

function errMsg(err: unknown, fallback: string): string {
  return err instanceof BizError ? err.message : fallback;
}

async function loadToday() {
  if (!empId.value) return;
  todayLoading.value = true;
  try {
    today.value = await fetchTodayAttendance(empId.value);
  } catch (err) {
    message.error(errMsg(err, '今日考勤加载失败'));
  } finally {
    todayLoading.value = false;
  }
}

async function loadLedgers() {
  loading.value = true;
  try {
    const page = await fetchAttendanceLedgers({
      empId: empId.value,
      leaveTypeId: filters.leaveTypeId,
      from: filters.range?.[0] ? `${filters.range[0]}T00:00:00` : undefined,
      to: filters.range?.[1] ? `${filters.range[1]}T23:59:59` : undefined,
      page: pagination.current,
      size: pagination.pageSize,
    });
    rows.value = page.records ?? [];
    total.value = page.total ?? 0;
  } catch (err) {
    message.error(errMsg(err, '台账加载失败'));
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function onTableChange(pag: { current?: number; pageSize?: number }) {
  if (pag.current) pagination.current = pag.current;
  if (pag.pageSize) pagination.pageSize = pag.pageSize;
  loadLedgers();
}

function search() {
  pagination.current = 1;
  loadLedgers();
}

function reset() {
  filters.leaveTypeId = undefined;
  filters.range = null;
  pagination.current = 1;
  loadLedgers();
}

function refreshAll() {
  loadToday();
  loadLedgers();
}

onMounted(async () => {
  loadToday();
  loadLedgers();
  try {
    const types = await listLeaveTypes();
    leaveTypeOptions.value = types.map((t) => ({ label: t.name, value: t.id }));
  } catch {
    leaveTypeOptions.value = [];
  }
});
</script>

<template>
  <div class="my-att">
    <section class="head">
      <div>
        <h2 class="head-title">我的考勤</h2>
        <p class="head-sub">今日打卡状态与假勤台账流水</p>
      </div>
      <a-button @click="refreshAll">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </section>

    <a-card :bordered="false" class="card" title="今日考勤" :loading="todayLoading">
      <a-descriptions :column="{ xs: 1, sm: 2, md: 4 }">
        <a-descriptions-item label="日期">{{ today?.workDate || '—' }}</a-descriptions-item>
        <a-descriptions-item label="班次">{{ today?.shiftName || '未排班' }}</a-descriptions-item>
        <a-descriptions-item label="上班打卡">{{ fmtTime(today?.punchIn ?? null) }}</a-descriptions-item>
        <a-descriptions-item label="下班打卡">{{ fmtTime(today?.punchOut ?? null) }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="todayStatusTag">{{ todayStatusText }}</a-tag>
        </a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-card :bordered="false" class="card" title="假勤台账">
      <a-space wrap class="filters">
        <a-select
          v-model:value="filters.leaveTypeId"
          :options="leaveTypeOptions"
          allow-clear
          placeholder="全部假期类型"
          style="width: 180px"
        />
        <a-range-picker v-model:value="filters.range" value-format="YYYY-MM-DD" style="width: 260px" />
        <a-button type="primary" @click="search">查询</a-button>
        <a-button @click="reset">重置</a-button>
      </a-space>

      <a-table
        row-key="id"
        :columns="[
          { title: '假期类型', dataIndex: 'leaveTypeName', key: 'leaveTypeName' },
          { title: '变动（天）', key: 'changeAmount', align: 'right' },
          { title: '业务参考', dataIndex: 'bizRef', key: 'bizRef' },
          { title: '发生时间', dataIndex: 'createdAt', key: 'createdAt', width: 190 },
        ]"
        :data-source="rows"
        :loading="loading"
        :pagination="{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total,
          showSizeChanger: true,
        }"
        :scroll="{ x: 720 }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'changeAmount'">
            <span :class="Number(record.changeAmount) < 0 ? 'minus' : 'plus'">
              {{ fmtAmount(record.changeAmount) }}
            </span>
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ fmtTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'bizRef'">{{ record.bizRef || '—' }}</template>
        </template>
        <template #emptyText>
          <EmptyHint description="暂无台账流水" hint="请假、加班补扣等假勤变动会在此留痕" />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.my-att {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
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

.filters {
  margin-bottom: var(--space-4);
}

.minus {
  color: var(--color-danger, #cf1322);
}

.plus {
  color: var(--color-success, #389e0d);
}
</style>
