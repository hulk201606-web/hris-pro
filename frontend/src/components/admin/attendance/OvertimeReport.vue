<script setup lang="ts">
/**
 * 加班统计报表（ATT-F-025）：按员工汇总加班次数与总时长。
 *
 * 次数与时长来自已审批通过的加班单；加班费由薪酬模块按倍率规则折算，
 * 不在本报表计算。
 */
import { onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { getOvertimeReport } from '../../../api/attendance';
import type { OvertimeStatItem } from '../../../api/attendance';

function monthRange(): [string, string] {
  const now = new Date();
  const first = new Date(now.getFullYear(), now.getMonth(), 1);
  const fmt = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  return [fmt(first), fmt(now)];
}

const range = ref<[string, string]>(monthRange());
const rows = ref<OvertimeStatItem[]>([]);
const loading = ref(false);

async function load() {
  if (!range.value || range.value.length !== 2) return;
  loading.value = true;
  try {
    rows.value = await getOvertimeReport(range.value[0], range.value[1]);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加班报表加载失败');
    rows.value = [];
  } finally {
    loading.value = false;
  }
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
      :locale="{ emptyText: '该区间暂无加班记录' }"
    >
      <a-table-column key="empId" title="员工 ID" data-index="empId" :width="160" />
      <a-table-column key="times" title="加班次数" data-index="times" :width="130" />
      <a-table-column key="totalHours" title="累计时长（小时）" :width="180">
        <template #default="{ record }">
          {{ record.totalHours === undefined || record.totalHours === null ? '-' : Number(record.totalHours).toFixed(1) }}
        </template>
      </a-table-column>
    </a-table>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>
