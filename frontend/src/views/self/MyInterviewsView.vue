<script setup lang="ts">
/**
 * 我的面谈（自助）：GET /prf/interviews/me，后端按当前登录员工过滤，只读展示。
 * PIP 进行中的记录提示员工关注跟进节点。
 */
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import EmptyHint from '../../components/admin/EmptyHint.vue';
import { errorText } from '../../components/admin/crud-helpers';
import { listMyInterviews } from '../../api/performance';
import type { InterviewRecord } from '../../types/api-batch2';

const rows = ref<InterviewRecord[]>([]);
const loading = ref(true);

const conclusionLabels: Record<string, string> = {
  IMPROVE: '改进',
  KEEP: '维持',
  PROMOTE: '晋升',
  PIP: '绩效改进',
};
const pipStatusLabels: Record<string, string> = {
  ONGOING: '进行中',
  PASSED: '通过',
  FAILED: '未通过',
};

const columns = [
  { title: '轮次', dataIndex: 'round', key: 'round', width: 80 },
  { title: '面谈日期', dataIndex: 'interviewDate', key: 'interviewDate', width: 120 },
  { title: '面谈人', dataIndex: 'interviewerEmpId', key: 'interviewerEmpId', width: 100 },
  { title: '结论', dataIndex: 'conclusion', key: 'conclusion', width: 110 },
  { title: 'PIP 起止', key: 'pipSpan', width: 200 },
  { title: 'PIP 状态', dataIndex: 'pipStatus', key: 'pipStatus', width: 110 },
  { title: '纪要', dataIndex: 'summary', key: 'summary', ellipsis: true },
];

const hasRows = computed(() => rows.value.length > 0);
const pipOngoingCount = computed(
  () => rows.value.filter((r) => r.conclusion === 'PIP' && r.pipStatus === 'ONGOING').length,
);

function pipSpan(record: InterviewRecord): string {
  if (record.conclusion !== 'PIP') return '—';
  return `${record.pipStartDate || '—'} ~ ${record.pipEndDate || '—'}`;
}

async function load() {
  loading.value = true;
  try {
    rows.value = (await listMyInterviews()).records ?? [];
  } catch (e) {
    message.error(errorText(e, '面谈记录加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="my-interviews">
    <section class="head">
      <h2 class="head-title">我的面谈</h2>
      <p class="head-sub">与我相关的绩效面谈纪要与 PIP 跟进状态</p>
    </section>

    <a-alert
      v-if="pipOngoingCount > 0"
      type="warning"
      show-icon
      :message="`你有 ${pipOngoingCount} 个绩效改进计划（PIP）进行中，请按约定节点跟进改进目标`"
      class="pip-alert"
    />

    <a-card :bordered="false" class="table-card">
      <a-table
        v-if="hasRows"
        row-key="id"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'conclusion'">
            <a-tag :color="record.conclusion === 'PIP' ? 'gold' : 'blue'">
              {{ conclusionLabels[record.conclusion] ?? record.conclusion }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'pipSpan'">{{ pipSpan(record) }}</template>
          <template v-else-if="column.key === 'pipStatus'">
            <a-tag v-if="record.pipStatus"
              :color="record.pipStatus === 'PASSED' ? 'green' : record.pipStatus === 'FAILED' ? 'red' : 'gold'">
              {{ pipStatusLabels[record.pipStatus] ?? record.pipStatus }}
            </a-tag>
            <span v-else>—</span>
          </template>
        </template>
      </a-table>
      <EmptyHint v-else-if="!loading" description="暂无面谈记录" />
    </a-card>
  </div>
</template>

<style scoped>
.my-interviews {
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

.pip-alert,
.table-card {
  border-radius: var(--radius-lg);
}
</style>
