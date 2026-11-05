<script setup lang="ts">
/**
 * 调薪轨迹抽屉（CMP-F-018）：按生效日倒序展示定薪版本链 + 缺失月份调整单补充。
 * source=PAY_VERSION 来自 cmp_employee_pay 版本链（权威定薪档案）；
 * source=ADJUSTMENT 来自调整单（金额为调整额，非定薪额）。金额为密域明文，服务端另校验 cmp:salary:read。
 */
import { ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { listEmployeePayHistory } from '../../../api/compensation';
import { errorText } from '../crud-helpers';
import EmptyHint from '../EmptyHint.vue';
import type { EmployeePayHistoryItem } from '../../../types/api-batch9';

const props = defineProps<{ open: boolean; empId: number | null }>();
const emit = defineEmits<{ 'update:open': [boolean] }>();

const loading = ref(false);
const rows = ref<EmployeePayHistoryItem[]>([]);

const SOURCE_LABEL: Record<string, string> = {
  PAY_VERSION: '定薪版本',
  ADJUSTMENT: '调整单',
};
const SOURCE_COLOR: Record<string, string> = {
  PAY_VERSION: 'blue',
  ADJUSTMENT: 'gold',
};

function amount(v: number | null): string {
  return v === null || v === undefined ? '—' : Number(v).toFixed(2);
}

async function load() {
  if (!props.empId) return;
  loading.value = true;
  try {
    rows.value = await listEmployeePayHistory(props.empId);
  } catch (e) {
    message.error(errorText(e, '调薪轨迹加载失败（需 cmp:salary:read）'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.open,
  (v) => {
    if (v) {
      rows.value = [];
      load();
    }
  },
);
</script>

<template>
  <a-drawer
    :open="open"
    title="调薪轨迹"
    width="560"
    @update:open="emit('update:open', $event)"
  >
    <a-spin :spinning="loading">
      <EmptyHint v-if="!loading && !rows.length" description="暂无调薪记录" />
      <a-timeline v-else>
        <a-timeline-item v-for="(item, index) in rows" :key="`${item.effectiveDate}-${index}`">
          <div class="line">
            <span class="date">{{ item.effectiveDate }}</span>
            <a-tag :color="SOURCE_COLOR[item.source] ?? 'default'">
              {{ SOURCE_LABEL[item.source] ?? item.source }}
            </a-tag>
            <span class="amount">{{ amount(item.baseAmount) }}</span>
          </div>
          <div class="reason">{{ item.reason || '未填写原因' }}</div>
          <div class="meta">
            操作人 {{ item.createdBy ?? '—' }} · 创建时间 {{ item.createdAt?.replace('T', ' ').slice(0, 16) }}
          </div>
        </a-timeline-item>
      </a-timeline>
    </a-spin>
  </a-drawer>
</template>

<style scoped>
.line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.date {
  font-weight: 600;
  color: var(--color-text-primary);
}
.amount {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-primary);
}
.reason {
  margin-top: 4px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
