<script setup lang="ts">
/**
 * 管理端-人力结构分析页（P1 聚合报表）：部门 / 雇佣类型 / 在职状态三个维度的人数分布。
 * deptScope=top 一级部门滚动聚合（默认），all 逐部门。权限 org:dept:read。
 */
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { SearchOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { getHeadcountStructure } from '../../../api/org';
import { useUserStore } from '../../../stores/user';
import type { HeadcountStructureResponse } from '../../../types/api-batch3';

const userStore = useUserStore();
const canRead = computed(() => userStore.hasPerm('org:dept:read'));

const HIRE_TYPE_LABELS: Record<string, string> = {
  FULLTIME: '全职',
  PARTTIME: '兼职',
  INTERN: '实习',
  CONSULTANT: '顾问',
};
const STATUS_LABELS: Record<number, string> = {
  0: '待入职',
  1: '试用',
  2: '在职',
  3: '离职中',
  4: '已离职',
};

const deptScope = ref<'top' | 'all'>('top');
const data = ref<HeadcountStructureResponse | null>(null);
const loading = ref(false);

const deptTotal = computed(() =>
  (data.value?.byDept ?? []).reduce((sum, d) => sum + (Number(d.count) || 0), 0),
);

const deptColumns = [
  { title: '部门', dataIndex: 'deptName', key: 'deptName', width: 260 },
  { title: '人数', dataIndex: 'count', key: 'count', width: 110, align: 'right' as const },
  { title: '占比', key: 'percent', width: 140 },
];

async function load() {
  if (!canRead.value) return;
  loading.value = true;
  try {
    data.value = await getHeadcountStructure(deptScope.value);
  } catch (e) {
    message.error(errorText(e, '人力结构数据加载失败'));
    data.value = null;
  } finally {
    loading.value = false;
  }
}

function percent(count: number): string {
  if (!deptTotal.value) return '—';
  return `${((count / deptTotal.value) * 100).toFixed(1)}%`;
}

onMounted(load);
</script>

<template>
  <div class="structure-page">
    <section class="head">
      <div>
        <h2 class="head-title">人力结构分析</h2>
        <p class="head-sub">
          在职状态口径：待入职/试用/在职/离职中/已离职；deptScope=top 时部门人数为一级部门滚动聚合（含下辖全部层级）
        </p>
      </div>
      <a-button :loading="loading" @click="load">
        <template #icon><SearchOutlined /></template>
        刷新
      </a-button>
    </section>

    <a-card :bordered="false" class="filter-card">
      <a-form layout="inline">
        <a-form-item label="部门口径">
          <a-radio-group v-model:value="deptScope" @change="load">
            <a-radio-button value="top">一级部门汇总</a-radio-button>
            <a-radio-button value="all">逐部门明细</a-radio-button>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-card>

    <a-row :gutter="16">
      <a-col :span="14">
        <a-card :bordered="false" class="panel-card" title="部门分布">
          <a-table
            row-key="deptId"
            :columns="deptColumns"
            :data-source="data?.byDept ?? []"
            :loading="loading"
            :pagination="false"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'percent'">
                <a-progress
                  :percent="deptTotal ? Math.round((record.count / deptTotal) * 100) : 0"
                  size="small"
                />
              </template>
            </template>
            <template #summary>
              <a-table-summary-row v-if="data?.byDept?.length">
                <a-table-summary-cell :index="0">合计</a-table-summary-cell>
                <a-table-summary-cell :index="1" align="right">{{ deptTotal }}</a-table-summary-cell>
                <a-table-summary-cell :index="2">100%</a-table-summary-cell>
              </a-table-summary-row>
            </template>
            <template #emptyText>
              <EmptyHint description="暂无部门数据" />
            </template>
          </a-table>
        </a-card>
      </a-col>
      <a-col :span="10">
        <a-card :bordered="false" class="panel-card" title="雇佣类型分布">
          <a-space direction="vertical" :size="12" class="stat-list">
            <a-statistic
              v-for="item in data?.byHireType ?? []"
              :key="item.hireType"
              :title="HIRE_TYPE_LABELS[item.hireType] || item.hireType"
              :value="item.count"
            />
          </a-space>
          <EmptyHint
            v-if="data && !data.byHireType?.length"
            description="暂无雇佣类型数据"
          />
        </a-card>
        <a-card :bordered="false" class="panel-card" title="在职状态分布">
          <a-space direction="vertical" :size="12" class="stat-list">
            <a-statistic
              v-for="item in data?.byStatus ?? []"
              :key="item.status"
              :title="STATUS_LABELS[item.status] || `状态 ${item.status}`"
              :value="item.count"
            />
          </a-space>
          <EmptyHint
            v-if="data && !data.byStatus?.length"
            description="暂无在职状态数据"
          />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<style scoped>
.structure-page {
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

.filter-card,
.panel-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.panel-card + .panel-card {
  margin-top: 16px;
}

.stat-list {
  width: 100%;
}
</style>
