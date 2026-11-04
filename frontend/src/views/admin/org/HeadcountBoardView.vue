<script setup lang="ts">
/**
 * 管理端-编制看板（ORG-F-011）：按部门 × 岗位 × 期间查看编制配额与占用。
 *
 * 两个需要正确呈现的业务语义：
 * 1. 空缺（vacancy）可为**负数**，表示超编；超编的岗位在入职/调动时会被
 *    服务端拦截（HeadcountIntegration fail-closed）。负数用醒目颜色标出。
 * 2. 占用率（usageRate）是 0-1 的小数，quota<=0 时服务端返回 0（除零守卫），
 *    因此这里统一转百分比展示，不对 quota=0 做特殊判断。
 */
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { listDepts, listHeadcountUsage, listPositions } from '../../../api/org';
import type { DeptNode, HeadcountUsage } from '../../../types/api';

function currentPeriod(): string {
  const now = new Date();
  return `${now.getFullYear()}Q${Math.floor(now.getMonth() / 3) + 1}`;
}

const deptOptions = ref<{ value: number; label: string }[]>([]);
const positionNames = ref<Record<number, string>>({});
const rows = ref<HeadcountUsage[]>([]);
const loading = ref(false);
const deptId = ref<number | undefined>(undefined);
const period = ref(currentPeriod());

/** 汇总：超编项单独计数，避免平均占用率把问题掩盖 */
const summary = computed(() => {
  const totalQuota = rows.value.reduce((s, r) => s + (r.quota ?? 0), 0);
  const totalOccupied = rows.value.reduce((s, r) => s + (r.occupied ?? 0), 0);
  const totalVacancy = rows.value.reduce((s, r) => s + (r.vacancy ?? 0), 0);
  const overstaffed = rows.value.filter((r) => (r.vacancy ?? 0) < 0).length;
  const rate = totalQuota > 0 ? totalOccupied / totalQuota : 0;
  return { totalQuota, totalOccupied, totalVacancy, overstaffed, rate };
});

async function loadCatalogs() {
  try {
    const [depts, positions] = await Promise.all([listDepts(false), listPositions()]);
    deptOptions.value = depts.map((d: DeptNode) => ({ value: d.id, label: `${d.name}（${d.path}）` }));
    positionNames.value = Object.fromEntries(positions.map((p) => [p.id, p.name]));
  } catch {
    // 目录加载失败不阻断看板（岗位名会退化为显示 ID）
  }
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listHeadcountUsage(deptId.value, period.value || undefined);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '编制数据加载失败');
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function reset() {
  deptId.value = undefined;
  period.value = currentPeriod();
  load();
}

function pct(v?: number): string {
  if (v === undefined || v === null) return '-';
  return `${(v * 100).toFixed(1)}%`;
}

onMounted(async () => {
  await loadCatalogs();
  await load();
});
</script>

<template>
  <div class="board">
    <section class="head">
      <h2 class="head-title">编制看板</h2>
      <p class="head-sub">
        空缺为负数表示超编；超编岗位的入职与调动会被服务端拦截，需先调整配额。
      </p>
    </section>

    <a-space class="toolbar" wrap>
      <span>部门</span>
      <a-select
        v-model:value="deptId"
        :options="deptOptions"
        allow-clear
        placeholder="全部部门"
        style="width: 240px"
        @change="load"
      />
      <span>期间</span>
      <a-input v-model:value="period" placeholder="如 2026Q4" style="width: 140px" @press-enter="load" />
      <a-button type="primary" :loading="loading" @click="load">查询</a-button>
      <a-button @click="reset">重置</a-button>
    </a-space>

    <a-row :gutter="16" class="stats">
      <a-col :span="6">
        <a-card :bordered="false">
          <a-statistic title="总编制" :value="summary.totalQuota" />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false">
          <a-statistic title="已占用" :value="summary.totalOccupied" />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false">
          <a-statistic title="总空缺" :value="summary.totalVacancy" />
          <a-tag v-if="summary.totalVacancy < 0" color="red">整体超编</a-tag>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false">
          <a-statistic title="平均占用率" :value="pct(summary.rate)" />
          <a-tag v-if="summary.overstaffed" color="red">{{ summary.overstaffed }} 个岗位超编</a-tag>
        </a-card>
      </a-col>
    </a-row>

    <a-table
      :data-source="rows"
      :loading="loading"
      row-key="positionId"
      size="middle"
      :pagination="false"
      :locale="{ emptyText: '该条件下暂无编制数据（可在「编制管理」中设置配额）' }"
    >
      <a-table-column key="deptId" title="部门" :width="200">
        <template #default="{ record }">
          {{ deptOptions.find((d) => d.value === record.deptId)?.label || record.deptId }}
        </template>
      </a-table-column>
      <a-table-column key="positionId" title="岗位" :width="200">
        <template #default="{ record }">
          {{ positionNames[record.positionId] || record.positionId }}
        </template>
      </a-table-column>
      <a-table-column key="period" title="期间" data-index="period" :width="110" />
      <a-table-column key="quota" title="编制" data-index="quota" :width="90" />
      <a-table-column key="occupied" title="已占用" data-index="occupied" :width="90" />
      <a-table-column key="vacancy" title="空缺" :width="100">
        <template #default="{ record }">
          <span>{{ record.vacancy }}</span>
          <a-tag v-if="(record.vacancy ?? 0) < 0" color="red">超编</a-tag>
        </template>
      </a-table-column>
      <a-table-column key="usageRate" title="占用率" :width="110">
        <template #default="{ record }">{{ pct(record.usageRate) }}</template>
      </a-table-column>
    </a-table>
  </div>
</template>

<style scoped>
.board {
  padding: 4px;
}
.head {
  margin-bottom: 16px;
}
.head-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}
.head-sub {
  margin: 0;
  color: var(--color-text-secondary, #666);
  font-size: 13px;
}
.toolbar {
  margin-bottom: 16px;
}
.stats {
  margin-bottom: 16px;
}
</style>
