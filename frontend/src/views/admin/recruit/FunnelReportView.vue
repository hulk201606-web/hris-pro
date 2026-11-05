<script setup lang="ts">
/**
 * 管理端-招聘漏斗报表（RCS-F-020）：各阶段去重候选人数与转化率。
 * 支持日期区间与需求单 ID 过滤；权限复用 rcu:requisition:read。
 */
import { onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { getFunnelReport } from '../../../api/recruit';
import type { FunnelItem } from '../../../api/recruit';

const STAGE_MAP: Record<string, string> = {
  APPLIED: '投递',
  SCREEN: '初筛',
  INTERVIEW: '面试',
  OFFER: 'Offer',
  HIRED: '入职',
};

const STAGE_ORDER = ['APPLIED', 'SCREEN', 'INTERVIEW', 'OFFER', 'HIRED'];

const filters = reactive({
  range: [] as string[],
  reqId: undefined as number | undefined,
});

const rows = ref<FunnelItem[]>([]);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    const data = await getFunnelReport({
      from: filters.range[0] ? `${filters.range[0]}T00:00:00` : undefined,
      to: filters.range[1] ? `${filters.range[1]}T23:59:59` : undefined,
      reqId: filters.reqId,
    });
    // 后端按阶段顺序返回，这里仅做展示排序兜底
    rows.value = [...data].sort(
      (a, b) => STAGE_ORDER.indexOf(a.stage) - STAGE_ORDER.indexOf(b.stage),
    );
  } catch (e) {
    message.error(errorText(e, '漏斗报表加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function stageText(stage: string): string {
  return STAGE_MAP[stage] ?? stage;
}

function conversionText(value: number): string {
  return `${Number(value).toFixed(2)}%`;
}

function conversionColor(value: number): string {
  if (value >= 60) return 'green';
  if (value >= 30) return 'blue';
  return 'orange';
}

const tableColumns = [
  { title: '阶段', dataIndex: 'stage', key: 'stage', width: 140 },
  { title: '去重候选人数', dataIndex: 'count', key: 'count', width: 140 },
  { title: '转化率（相对投递）', dataIndex: 'conversion', key: 'conversion' },
];

onMounted(load);
</script>

<template>
  <div class="funnel-page">
    <section class="head">
      <div>
        <h2 class="head-title">招聘漏斗</h2>
        <p class="head-sub">统计各阶段去重候选人数与相对投递的转化率，可按日期区间与需求单过滤</p>
      </div>
      <a-button @click="load">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </section>

    <a-card :bordered="false" class="panel">
      <a-space wrap class="filters">
        <a-range-picker v-model:value="filters.range" value-format="YYYY-MM-DD" />
        <a-input-number
          v-model:value="filters.reqId"
          :min="1"
          placeholder="需求单 ID（选填）"
          style="width: 180px"
        />
        <a-button type="primary" @click="load">查询</a-button>
      </a-space>

      <a-table
        row-key="stage"
        :columns="tableColumns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'stage'">
            <a-tag color="blue">{{ stageText(record.stage) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'conversion'">
            <div class="conv-cell">
              <a-progress
                :percent="Math.min(Number(record.conversion), 100)"
                :stroke-color="conversionColor(record.conversion)"
                :show-info="false"
                size="small"
                class="conv-bar"
              />
              <span class="conv-text">{{ conversionText(record.conversion) }}</span>
            </div>
          </template>
        </template>
        <template #emptyText>
          <EmptyHint description="暂无漏斗数据" hint="调整过滤条件后点击「查询」" />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.funnel-page {
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

.panel {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.filters {
  margin-bottom: var(--space-4);
}

.conv-cell {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.conv-bar {
  flex: 1;
  max-width: 320px;
  margin: 0;
}

.conv-text {
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
</style>
