<script setup lang="ts">
/**
 * 管理端-目标管理：按 cycleId:empId:dimension 整组提交（PRF-F-003）。
 *
 * 契约要点（后端 GoalService.batchSave / GoalWeightRule）：
 * - POST /prf/goals/batch 为整组替换，权重合计必须恰好 100%；
 * - 不足/超差返回 PARAM_INVALID，消息形如「目标权重合计必须为 100%，当前合计 85%」，
 *   前端直接透出后端消息，不自行改写口径；
 * - 单条新增/编辑仅拦「合计超 100」，本页统一改用整组提交避免中间态歧义。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { DeleteOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { batchSaveGoals, listGoals } from '../../../api/performance';
import { useUserStore } from '../../../stores/user';
import type { Goal } from '../../../types/admin';
import type { GoalBatchItem } from '../../../types/api-batch9';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('prf:goal:write'));

const rows = ref<Goal[]>([]);
const loading = ref(false);
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });

const filter = reactive({ cycleId: undefined as number | undefined, empId: undefined as number | undefined, dimension: 'KPI' });

const batchOpen = ref(false);
const submitting = ref(false);
const batchScope = reactive({ cycleId: 0, empId: 0, dimension: 'KPI' });
const items = ref<GoalBatchItem[]>([]);

const totalWeight = computed(() =>
  items.value.reduce((sum, it) => sum + (Number(it.weight) || 0), 0),
);
const weightValid = computed(() => Math.round(totalWeight.value * 100) === 10000);

async function load() {
  loading.value = true;
  try {
    const page = await listGoals(pagination.current, pagination.pageSize);
    rows.value = page.records;
    pagination.total = page.total;
  } catch (e) {
    message.error(errorText(e, '目标列表加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function onTableChange(p: { current?: number; pageSize?: number }) {
  if (p.current) pagination.current = p.current;
  if (p.pageSize) pagination.pageSize = p.pageSize;
  load();
}

/** 打开整组提交：按当前筛选键回填该组已有目标，避免误清空其他维度 */
function openBatch() {
  if (!filter.cycleId || !filter.empId) return message.warning('请先填写周期 ID 与员工 ID');
  const dimension = (filter.dimension || 'KPI').trim().toUpperCase();
  batchScope.cycleId = filter.cycleId;
  batchScope.empId = filter.empId;
  batchScope.dimension = dimension;
  const existing = rows.value.filter(
    (g) =>
      g.cycleId === batchScope.cycleId &&
      g.empId === batchScope.empId &&
      (g.dimension ?? 'KPI').toUpperCase() === dimension,
  );
  items.value = existing.length
    ? existing.map((g) => ({
        title: g.title,
        weight: Number(g.weight),
        targetValue: g.targetValue ?? '',
        resultValue: g.resultValue ?? '',
      }))
    : [{ title: '', weight: 0, targetValue: '', resultValue: '' }];
  batchOpen.value = true;
}

function addItem() {
  items.value.push({ title: '', weight: 0, targetValue: '', resultValue: '' });
}

function removeItem(index: number) {
  if (items.value.length <= 1) return message.warning('至少保留一条目标');
  items.value.splice(index, 1);
}

async function handleSubmit() {
  if (items.value.some((it) => !it.title.trim())) return message.warning('请填写每条目标的标题');
  if (items.value.some((it) => !it.weight || it.weight <= 0)) return message.warning('目标权重须大于 0');
  submitting.value = true;
  try {
    await batchSaveGoals({
      cycleId: batchScope.cycleId,
      empId: batchScope.empId,
      dimension: batchScope.dimension,
      items: items.value,
    });
    message.success('整组提交成功');
    batchOpen.value = false;
    await load();
  } catch (e) {
    // 后端消息含「当前合计 xx%」，直接透出便于对齐权重口径
    message.error(errorText(e, '整组提交失败'));
  } finally {
    submitting.value = false;
  }
}

/** 调整：定位到该行所属组并打开整组提交（整组替换语义下不能单行删除） */
function handleAdjust(record: Goal) {
  filter.cycleId = record.cycleId;
  filter.empId = record.empId;
  filter.dimension = record.dimension ?? 'KPI';
  openBatch();
}

function fmtTime(v?: string): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—';
}

onMounted(load);
</script>

<template>
  <div class="goal-page">
    <section class="head">
      <div>
        <h2 class="head-title">目标管理</h2>
        <p class="head-sub">同周期 + 同员工 + 同维度整组提交，权重合计必须为 100%</p>
      </div>
      <a-space>
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canWrite" type="primary" @click="openBatch">
          <template #icon><PlusOutlined /></template>
          整组提交
        </a-button>
      </a-space>
    </section>

    <a-card :bordered="false" class="filter-card">
      <a-form layout="inline" class="filter-form">
        <a-form-item label="周期 ID">
          <a-input-number v-model:value="filter.cycleId" :min="1" placeholder="cycleId" />
        </a-form-item>
        <a-form-item label="员工 ID">
          <a-input-number v-model:value="filter.empId" :min="1" placeholder="empId" />
        </a-form-item>
        <a-form-item label="维度">
          <a-input v-model:value="filter.dimension" placeholder="KPI" style="width: 140px" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" ghost :disabled="!canWrite" @click="openBatch">整组提交</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card :bordered="false" class="table-card">
      <a-table
        row-key="id"
        :data-source="rows"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1100 }"
        @change="onTableChange"
      >
        <a-table-column key="title" title="目标" />
        <a-table-column key="dimension" title="维度" :width="110">
          <template #default="{ record }">{{ record.dimension ?? 'KPI' }}</template>
        </a-table-column>
        <a-table-column key="cycleId" title="周期 ID" data-index="cycleId" :width="100" />
        <a-table-column key="empId" title="员工 ID" data-index="empId" :width="100" />
        <a-table-column key="weight" title="权重(%)" data-index="weight" :width="100" />
        <a-table-column key="targetValue" title="目标值" :width="140">
          <template #default="{ record }">{{ record.targetValue || '—' }}</template>
        </a-table-column>
        <a-table-column key="resultValue" title="实际值" :width="140">
          <template #default="{ record }">{{ record.resultValue || '—' }}</template>
        </a-table-column>
        <a-table-column key="createdAt" title="创建时间" :width="160">
          <template #default="{ record }">{{ fmtTime(record.createdAt) }}</template>
        </a-table-column>
        <a-table-column key="action" title="操作" :width="120" fixed="right">
          <template #default="{ record }">
            <a-button v-if="canWrite" type="link" size="small" @click="handleAdjust(record)">调整</a-button>
          </template>
        </a-table-column>
        <template #emptyText>
          <EmptyHint description="暂无目标" hint="填写周期与员工后点击「整组提交」录入目标组" />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="batchOpen"
      :title="`整组提交（周期 ${batchScope.cycleId} / 员工 ${batchScope.empId} / ${batchScope.dimension}）`"
      :confirm-loading="submitting"
      :mask-closable="false"
      ok-text="提交"
      cancel-text="取消"
      width="820px"
      @ok="handleSubmit"
    >
      <a-alert
        :type="weightValid ? 'success' : 'warning'"
        show-icon
        class="weight-tip"
        :message="`当前合计 ${totalWeight}%${weightValid ? '' : '（提交需恰好 100%）'}`"
      />
      <div class="item-list">
        <div v-for="(item, index) in items" :key="index" class="item-row">
          <a-input v-model:value="item.title" placeholder="目标标题" class="col-title" />
          <a-input-number v-model:value="item.weight" :min="0" :max="100" placeholder="权重" class="col-weight" />
          <a-input v-model:value="item.targetValue" placeholder="目标值" class="col-value" />
          <a-input v-model:value="item.resultValue" placeholder="实际值" class="col-value" />
          <a-button type="text" danger size="small" @click="removeItem(index)">
            <template #icon><DeleteOutlined /></template>
          </a-button>
        </div>
      </div>
      <a-button type="dashed" block @click="addItem">
        <template #icon><PlusOutlined /></template>
        增加一条目标
      </a-button>
    </a-modal>
  </div>
</template>

<style scoped>
.goal-page {
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
.weight-tip {
  margin-bottom: var(--space-3);
}
.item-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}
.item-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.col-title {
  flex: 2;
}
.col-weight {
  width: 96px;
}
.col-value {
  flex: 1;
}
</style>
