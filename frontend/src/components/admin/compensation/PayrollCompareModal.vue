<script setup lang="ts">
/**
 * 薪酬环比对比弹窗（CMP-F-013a）：base=上期批次，current=当前批次，diff=current-base。
 * 后端 changeRate=netDiff/baseNet（baseNet 为 0 或缺失时为 null），此处按比率转百分比展示。
 */
import { ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { comparePayrollRun } from '../../../api/compensation';
import { errorText } from '../crud-helpers';
import type { PayrollCompareResult, PayrollEmployeeCompare } from '../../../types/api-batch9';

const props = defineProps<{ open: boolean; runId: number | null }>();
const emit = defineEmits<{ 'update:open': [boolean] }>();

const loading = ref(false);
const result = ref<PayrollCompareResult | null>(null);

function num(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : Number(v).toFixed(2);
}

function rate(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : `${(Number(v) * 100).toFixed(2)}%`;
}

async function load() {
  if (!props.runId) return;
  loading.value = true;
  try {
    result.value = await comparePayrollRun(props.runId);
  } catch (e) {
    message.error(errorText(e, '环比对比加载失败'));
    result.value = null;
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.open,
  (v) => {
    if (v) {
      result.value = null;
      load();
    }
  },
);
</script>

<template>
  <a-modal
    :open="open"
    title="算薪环比对比"
    width="920px"
    :footer="null"
    @update:open="emit('update:open', $event)"
  >
    <a-spin :spinning="loading">
      <a-alert
        v-if="result && result.baseRunId === null"
        type="warning"
        show-icon
        class="note"
        :message="result.note || '同期间无上期批次，无法对比'"
      />
      <template v-if="result && result.baseRunId !== null">
        <a-descriptions :column="4" size="small" bordered class="summary">
          <a-descriptions-item label="当前批次">#{{ result.runId }} / {{ result.periodCode }}</a-descriptions-item>
          <a-descriptions-item label="对比批次">#{{ result.baseRunId }} / {{ result.basePeriodCode }}</a-descriptions-item>
          <a-descriptions-item label="员工数">{{ result.employeeCount }}</a-descriptions-item>
          <a-descriptions-item label="实发变化率">{{ rate(result.changeRate) }}</a-descriptions-item>
          <a-descriptions-item label="应发差额">{{ num(result.grossDiff) }}</a-descriptions-item>
          <a-descriptions-item label="实发差额">{{ num(result.netDiff) }}</a-descriptions-item>
        </a-descriptions>

        <a-table
          :data-source="result.employees"
          row-key="empId"
          size="small"
          :pagination="false"
          :scroll="{ y: 420 }"
          class="table"
        >
          <a-table-column key="empId" title="员工 ID" data-index="empId" :width="100" />
          <a-table-column key="grossDiff" title="应发差额" :width="130">
            <template #default="{ record }">{{ num(record.grossDiff) }}</template>
          </a-table-column>
          <a-table-column key="netDiff" title="实发差额" :width="130">
            <template #default="{ record }">{{ num(record.netDiff) }}</template>
          </a-table-column>
          <a-table-column key="changeRate" title="实发变化率" :width="130">
            <template #default="{ record }">{{ rate(record.changeRate) }}</template>
          </a-table-column>
          <a-table-column key="items" title="明细项数" :width="110">
            <template #default="{ record }">{{ record.items?.length ?? 0 }}</template>
          </a-table-column>
          <template #expandedRowRender="{ record }">
            <a-table
              :data-source="(record as PayrollEmployeeCompare).items"
              row-key="code"
              size="small"
              :pagination="false"
            >
              <a-table-column key="code" title="薪资项" data-index="code" />
              <a-table-column key="base" title="上期" :width="120">
                <template #default="{ record: item }">{{ num(item.base) }}</template>
              </a-table-column>
              <a-table-column key="current" title="本期" :width="120">
                <template #default="{ record: item }">{{ num(item.current) }}</template>
              </a-table-column>
              <a-table-column key="diff" title="差额" :width="120">
                <template #default="{ record: item }">{{ num(item.diff) }}</template>
              </a-table-column>
            </a-table>
          </template>
        </a-table>
      </template>
      <a-empty v-else-if="!loading" description="暂无可对比数据" />
    </a-spin>
  </a-modal>
</template>

<style scoped>
.note {
  margin-bottom: 12px;
}
.summary {
  margin-bottom: 12px;
}
.table {
  margin-top: 4px;
}
</style>
