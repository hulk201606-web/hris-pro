<script setup lang="ts">
/**
 * 管理端-多法人成本报表页（CMP-F-017）：按期间输出法人维度成本合并汇总。
 * 金额千分位展示，底部附人数与金额合计行。taxTotal 后端暂未落地（恒 null），展示为 —。
 * 权限：cmp:payroll-run:read（报表输出为聚合总额，不挂密域权限）。
 */
import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import { SearchOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { legalEntityCostReport } from '../../../api/compensation';
import { useUserStore } from '../../../stores/user';
import type { LegalEntityCostRow } from '../../../types/api-batch3';

const userStore = useUserStore();
const canRead = computed(() => userStore.hasPerm('cmp:payroll-run:read'));

const periodCode = ref('');
const rows = ref<LegalEntityCostRow[]>([]);
const loading = ref(false);
const queried = ref(false);

const columns = [
  { title: '法人主体', dataIndex: 'legalEntityName', key: 'legalEntityName', width: 220 },
  { title: '人数', dataIndex: 'headcount', key: 'headcount', width: 90, align: 'right' as const },
  { title: '应发合计', dataIndex: 'grossTotal', key: 'grossTotal', width: 150, align: 'right' as const },
  { title: '扣减合计', dataIndex: 'deductionTotal', key: 'deductionTotal', width: 150, align: 'right' as const },
  { title: '个税合计', dataIndex: 'taxTotal', key: 'taxTotal', width: 130, align: 'right' as const },
  { title: '实发合计', dataIndex: 'netTotal', key: 'netTotal', width: 150, align: 'right' as const },
];

/** 千分位格式化（后端 BigDecimal 以字符串/数字下发；null 展示 —） */
function fmt(v?: string | number | null): string {
  if (v === null || v === undefined || v === '') return '—';
  const num = Number(v);
  if (!Number.isFinite(num)) return String(v);
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

interface SummaryRow {
  key: string;
  legalEntityName: string;
  headcount: number;
  grossTotal: number;
  deductionTotal: number;
  taxTotal: number | null;
  netTotal: number;
}

/** 合计行：人数与各金额列求和；taxTotal 全空则为 null */
const summary = computed<SummaryRow>(() => {
  let taxAllNull = true;
  let taxSum = 0;
  const acc = {
    headcount: 0,
    grossTotal: 0,
    deductionTotal: 0,
    netTotal: 0,
  };
  for (const r of rows.value) {
    acc.headcount += Number(r.headcount) || 0;
    acc.grossTotal += Number(r.grossTotal) || 0;
    acc.deductionTotal += Number(r.deductionTotal) || 0;
    acc.netTotal += Number(r.netTotal) || 0;
    if (r.taxTotal !== null && r.taxTotal !== undefined && r.taxTotal !== '') {
      taxAllNull = false;
      taxSum += Number(r.taxTotal) || 0;
    }
  }
  return {
    key: '__summary__',
    legalEntityName: '合计',
    headcount: acc.headcount,
    grossTotal: acc.grossTotal,
    deductionTotal: acc.deductionTotal,
    taxTotal: taxAllNull ? null : taxSum,
    netTotal: acc.netTotal,
  };
});

async function load() {
  const period = periodCode.value.trim();
  if (!period) {
    message.warning('请输入期间编码（如 2026-10）');
    return;
  }
  if (!canRead.value) return;
  loading.value = true;
  try {
    rows.value = await legalEntityCostReport(period);
    queried.value = true;
  } catch (e) {
    message.error(errorText(e, '报表加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="cost-page">
    <section class="head">
      <div>
        <h2 class="head-title">多法人成本报表</h2>
        <p class="head-sub">
          按期间合并各法人主体的应发/扣减/实发汇总；扣减合计 = 应发 - 实发（社保公积金、个税及其他扣减，MVP 不拆分）
        </p>
      </div>
    </section>

    <a-card :bordered="false" class="filter-card">
      <a-form layout="inline" class="filter-form">
        <a-form-item label="期间编码">
          <a-input
            v-model:value="periodCode"
            placeholder="如 2026-10"
            style="width: 160px"
            allow-clear
            @press-enter="load"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="loading" @click="load">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card :bordered="false" class="table-card">
      <a-table
        row-key="legalEntityId"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'grossTotal'">{{ fmt(record.grossTotal) }}</template>
          <template v-else-if="column.key === 'deductionTotal'">{{ fmt(record.deductionTotal) }}</template>
          <template v-else-if="column.key === 'taxTotal'">{{ fmt(record.taxTotal) }}</template>
          <template v-else-if="column.key === 'netTotal'">{{ fmt(record.netTotal) }}</template>
        </template>
        <template #summary>
          <a-table-summary-row v-if="rows.length" class="summary-row">
            <a-table-summary-cell :index="0">合计</a-table-summary-cell>
            <a-table-summary-cell :index="1" align="right">{{ summary.headcount }}</a-table-summary-cell>
            <a-table-summary-cell :index="2" align="right">{{ fmt(summary.grossTotal) }}</a-table-summary-cell>
            <a-table-summary-cell :index="3" align="right">{{ fmt(summary.deductionTotal) }}</a-table-summary-cell>
            <a-table-summary-cell :index="4" align="right">{{ fmt(summary.taxTotal) }}</a-table-summary-cell>
            <a-table-summary-cell :index="5" align="right">{{ fmt(summary.netTotal) }}</a-table-summary-cell>
          </a-table-summary-row>
        </template>
        <template #emptyText>
          <EmptyHint
            description="暂无报表数据"
            :hint="queried ? '该期间无成本汇总记录' : '输入期间编码后点击「查询」生成报表'"
          />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.cost-page {
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

.summary-row :deep(td) {
  background: var(--color-bg-secondary, #fafafa);
  font-weight: 600;
}
</style>
