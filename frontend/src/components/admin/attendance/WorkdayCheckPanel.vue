<script setup lang="ts">
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { errorText } from '../crud-helpers';
import { checkWorkday } from '../../../api/attendance-holiday';
import type { WorkdayCheck } from '../../../api/attendance-holiday';

/**
 * 工作日判定核对面板（ATT-F-021）。
 * 调休制度下周末可能上班、周一可能放假，只按星期几推断会算错，
 * 因此维护日历时必须能直接验证某天的判定结果与依据。
 */
const SOURCE_MAP: Record<string, string> = {
  HOLIDAY: '日历配置：法定节假日',
  WORKDAY: '日历配置：调班补班',
  WEEKDAY: '按周一至周五推断',
};

function today(): string {
  const d = new Date();
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

const checkDate = ref<any>(today());
const checkResult = ref<WorkdayCheck | null>(null);
const checking = ref(false);

async function runCheck() {
  if (!checkDate.value) {
    message.warning('请选择要判定的日期');
    return;
  }
  checking.value = true;
  try {
    checkResult.value = await checkWorkday(checkDate.value);
  } catch (e) {
    message.error(errorText(e, '工作日判定失败'));
    checkResult.value = null;
  } finally {
    checking.value = false;
  }
}
</script>

<template>
  <a-card :bordered="false" class="panel" title="工作日判定（核对用）">
    <a-space wrap>
      <a-date-picker v-model:value="checkDate" value-format="YYYY-MM-DD" />
      <a-button :loading="checking" @click="runCheck">判定</a-button>
      <template v-if="checkResult">
        <a-tag :color="checkResult.workday ? 'blue' : 'red'">
          {{ checkResult.workday ? '应出勤' : '休息日' }}
        </a-tag>
        <span class="result-source">{{ SOURCE_MAP[checkResult.source] ?? checkResult.source }}</span>
      </template>
    </a-space>
  </a-card>
</template>

<style scoped>
.panel {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.result-source {
  color: var(--color-text-secondary);
}
</style>
