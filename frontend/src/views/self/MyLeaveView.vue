<script setup lang="ts">
/**
 * 我的假期（自助）：额度总览 + 台账流水。
 * 额度调 GET /att/leave-quotas/me；台账调 GET /att/leave-quotas/me/ledgers（后端按当前员工过滤）。
 */
import { computed, onMounted, ref } from 'vue';
import EmptyHint from '../../components/admin/EmptyHint.vue';
import { fetchMyLeaveLedgers, fetchMyQuotaBalance } from '../../api/self';
import type { MyLeaveLedger, MyLeaveQuota } from '../../api/self';

const quotas = ref<MyLeaveQuota[]>([]);
const ledgers = ref<MyLeaveLedger[]>([]);
const loading = ref(true);
const errorText = ref('');

const quotaColumns = [
  { title: '假期类型', dataIndex: 'leaveTypeName', key: 'leaveTypeName' },
  { title: '周期', dataIndex: 'period', key: 'period' },
  { title: '总额度（天）', dataIndex: 'totalQuota', key: 'totalQuota', align: 'right' as const },
  { title: '已用（天）', dataIndex: 'usedQuota', key: 'usedQuota', align: 'right' as const },
  { title: '可用（天）', key: 'availableQuota', align: 'right' as const },
];

const ledgerColumns = [
  { title: '假期类型', dataIndex: 'leaveTypeName', key: 'leaveTypeName' },
  { title: '变动（天）', key: 'changeAmount', align: 'right' as const },
  { title: '业务参考', dataIndex: 'bizRef', key: 'bizRef' },
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt' },
];

const hasQuota = computed(() => quotas.value.length > 0);
const hasLedger = computed(() => ledgers.value.length > 0);

function fmtAmount(v: unknown): string {
  const n = Number(v);
  return Number.isFinite(n) ? n.toFixed(2) : '-';
}

function fmtTime(v: string | null): string {
  return v ? v.replace('T', ' ').slice(0, 19) : '-';
}

function statusText(refCode: string | null): string {
  if (!refCode) return '-';
  if (refCode.startsWith('LEAVE_CONSUME')) return '请假扣减';
  if (refCode.startsWith('LEAVE_REFUND')) return '销假退还';
  if (refCode.startsWith('CARRY_OVER')) return '额度结转';
  if (refCode.startsWith('GRANT')) return '额度发放';
  return refCode;
}

async function load() {
  loading.value = true;
  errorText.value = '';
  try {
    const [balance, ledger] = await Promise.all([fetchMyQuotaBalance(), fetchMyLeaveLedgers()]);
    quotas.value = balance.quotas ?? [];
    ledgers.value = ledger ?? [];
  } catch (e) {
    errorText.value = e instanceof Error ? e.message : '假期数据加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="my-leave">
    <section class="head">
      <h2 class="head-title">我的假期</h2>
      <p class="head-sub">假期额度与变动流水（按年度周期归集）</p>
    </section>

    <a-alert v-if="errorText" type="error" :message="errorText" show-icon class="error" />

    <a-card title="假期额度" :bordered="false" class="block-card">
      <a-table
        :columns="quotaColumns"
        :data-source="quotas"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'leaveTypeName'">
            {{ (record as MyLeaveQuota).leaveTypeName || `假种 ${(record as MyLeaveQuota).leaveTypeId}` }}
          </template>
          <template v-else-if="column.key === 'totalQuota'">
            {{ fmtAmount((record as MyLeaveQuota).totalQuota) }}
          </template>
          <template v-else-if="column.key === 'usedQuota'">
            {{ fmtAmount((record as MyLeaveQuota).usedQuota) }}
          </template>
          <template v-else-if="column.key === 'availableQuota'">
            <span class="available">{{ fmtAmount((record as MyLeaveQuota).availableQuota) }}</span>
          </template>
        </template>
        <template #emptyText>
          <EmptyHint
            description="暂无假期额度记录"
            hint="额度由人事按年度发放，发放后即可在此查看"
          />
        </template>
      </a-table>
    </a-card>

    <a-card title="额度流水" :bordered="false" class="block-card">
      <a-table
        :columns="ledgerColumns"
        :data-source="ledgers"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: false }"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'leaveTypeName'">
            {{ (record as MyLeaveLedger).leaveTypeName || `假种 ${(record as MyLeaveLedger).leaveTypeId}` }}
          </template>
          <template v-else-if="column.key === 'changeAmount'">
            <span
              :class="['change', { negative: Number((record as MyLeaveLedger).changeAmount) < 0 }]"
            >
              {{ fmtAmount((record as MyLeaveLedger).changeAmount) }}
            </span>
          </template>
          <template v-else-if="column.key === 'bizRef'">
            {{ statusText((record as MyLeaveLedger).bizRef) }}
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ fmtTime((record as MyLeaveLedger).createdAt) }}
          </template>
        </template>
        <template #emptyText>
          <EmptyHint description="暂无额度变动流水" hint="请假审批通过后会产生扣减流水" />
        </template>
      </a-table>
    </a-card>

    <p v-if="!loading && !errorText && !hasQuota && !hasLedger" class="fallback">
      若确认已有假期额度，请联系人事核查发放记录。
    </p>
  </div>
</template>

<style scoped>
.my-leave {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
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

.block-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.error {
  border-radius: var(--radius-md);
}

.available {
  font-weight: 600;
  color: var(--color-primary, #1677ff);
}

.change {
  color: #389e0d;
}

.change.negative {
  color: #cf1322;
}

.fallback {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
