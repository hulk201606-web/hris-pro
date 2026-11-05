<script setup lang="ts">
/**
 * 出勤率报表（ATT-F-025）：按员工汇总工作天数与迟到/早退/缺勤/请假，给出出勤率。
 *
 * 日期范围默认本月。出勤率是 0-1 的小数，统一转百分比展示。
 */
import { onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { getAttendanceRateReport } from '../../../api/attendance-report';
import type { AttendanceRateItem } from '../../../api/attendance-report';

function monthRange(): [string, string] {
  const now = new Date();
  const first = new Date(now.getFullYear(), now.getMonth(), 1);
  const fmt = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  return [fmt(first), fmt(now)];
}

const range = ref<[string, string]>(monthRange());
const rows = ref<AttendanceRateItem[]>([]);
const loading = ref(false);

async function load() {
  if (!range.value || range.value.length !== 2) return;
  loading.value = true;
  try {
    rows.value = await getAttendanceRateReport(range.value[0], range.value[1]);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '出勤率报表加载失败');
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function pct(v?: number): string {
  return v === undefined || v === null ? '-' : `${(v * 100).toFixed(2)}%`;
}

onMounted(load);
</script>

<template>
  <div>
    <a-space class="toolbar">
      <span>日期范围</span>
      <a-range-picker v-model:value="range" value-format="YYYY-MM-DD" @change="load" />
      <a-button type="primary" :loading="loading" @click="load">查询</a-button>
    </a-space>

    <a-table
      :data-source="rows"
      :loading="loading"
      row-key="empId"
      size="middle"
      :pagination="{ pageSize: 20, showTotal: (n: number) => `共 ${n} 人` }"
      :locale="{ emptyText: '该区间暂无考勤数据' }"
    >
      <a-table-column key="empId" title="员工 ID" data-index="empId" :width="130" />
      <a-table-column key="workDays" title="应出勤" data-index="workDays" :width="100" />
      <a-table-column key="normalDays" title="正常" data-index="normalDays" :width="90" />
      <a-table-column key="lateDays" title="迟到" data-index="lateDays" :width="90" />
      <a-table-column key="earlyDays" title="早退" data-index="earlyDays" :width="90" />
      <a-table-column key="absentDays" title="旷工" data-index="absentDays" :width="90" />
      <a-table-column key="leaveDays" title="请假" data-index="leaveDays" :width="90" />
      <a-table-column key="attendanceRate" title="出勤率" :width="110">
        <template #default="{ record }">{{ pct(record.attendanceRate) }}</template>
      </a-table-column>
    </a-table>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>
