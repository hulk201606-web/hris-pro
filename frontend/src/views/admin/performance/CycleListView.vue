<script setup lang="ts">
/**
 * 管理端-考核周期：四态状态机（DRAFT/ACTIVE/SCORING/CLOSED）+ 白名单流转。
 *
 * 契约要点（后端 CycleService/CycleStateMachine）：
 * - 新建固定 DRAFT；编辑/删除仅 DRAFT 可执行；
 * - 状态迁移只走 POST /prf/cycles/{id}/transition（update 夹带状态会被拒）；
 * - 白名单严格单向：DRAFT→ACTIVE→SCORING→CLOSED，不显示回退/跳级入口。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import { ReloadOutlined, SwapRightOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import {
  createCycle,
  deleteCycle,
  listCycles,
  transitionCycle,
  updateCycle,
} from '../../../api/performance';
import { useUserStore } from '../../../stores/user';
import type { Cycle } from '../../../types/admin';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('prf:cycle:write'));

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '进行中',
  SCORING: '评分中',
  CLOSED: '已结束',
};
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  ACTIVE: 'green',
  SCORING: 'gold',
  CLOSED: 'default',
};
/** 历史值归一：读侧与后端 CycleStatus.resolve 保持同口径 */
const LEGACY: Record<string, string> = {
  NOT_STARTED: 'DRAFT',
  TODO: 'DRAFT',
  IN_PROGRESS: 'ACTIVE',
  RUNNING: 'ACTIVE',
  CALIBRATING: 'SCORING',
  PUBLISHED: 'CLOSED',
  ARCHIVED: 'CLOSED',
};
/** 白名单下一跳：无下一跳的状态不渲染流转按钮 */
const NEXT_TARGET: Record<string, string> = {
  DRAFT: 'ACTIVE',
  ACTIVE: 'SCORING',
  SCORING: 'CLOSED',
};

function norm(raw: string): string {
  return LEGACY[raw] ?? raw;
}

const rows = ref<Cycle[]>([]);
const loading = ref(false);
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });

const modalOpen = ref(false);
const editingId = ref<number | null>(null);
const submitting = ref(false);
const form = reactive({ planId: undefined as number | undefined, periodCode: '', startDate: '', endDate: '' });

const modalTitle = computed(() => (editingId.value ? '编辑考核周期' : '新建考核周期'));

async function load() {
  loading.value = true;
  try {
    const page = await listCycles(pagination.current, pagination.pageSize);
    rows.value = page.records;
    pagination.total = page.total;
  } catch (e) {
    message.error(errorText(e, '周期列表加载失败'));
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

function openCreate() {
  editingId.value = null;
  Object.assign(form, { planId: undefined, periodCode: '', startDate: '', endDate: '' });
  modalOpen.value = true;
}

function openEdit(record: Cycle) {
  if (norm(record.status) !== 'DRAFT') return message.warning('仅草稿周期可编辑，状态迁移请用「流转」');
  editingId.value = record.id;
  Object.assign(form, {
    planId: record.planId,
    periodCode: record.periodCode,
    startDate: record.startDate,
    endDate: record.endDate,
  });
  modalOpen.value = true;
}

/** 编辑/新建均不提交 status：新建由后端固定 DRAFT，编辑不得夹带状态 */
async function handleSubmit() {
  if (!form.planId || !form.periodCode.trim() || !form.startDate || !form.endDate) {
    return message.warning('请完整填写方案、周期编码与起止日期');
  }
  submitting.value = true;
  try {
    const payload = {
      planId: form.planId,
      periodCode: form.periodCode.trim(),
      startDate: form.startDate,
      endDate: form.endDate,
    };
    if (editingId.value) {
      await updateCycle(editingId.value, payload);
      message.success('保存成功');
    } else {
      await createCycle(payload);
      message.success('创建成功（初始状态：草稿）');
    }
    modalOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, '保存失败'));
  } finally {
    submitting.value = false;
  }
}

function confirmTransition(record: Cycle) {
  const target = NEXT_TARGET[norm(record.status)];
  if (!target) return;
  Modal.confirm({
    title: '周期状态流转',
    content: `确认将周期「${record.periodCode}」由 ${STATUS_LABEL[norm(record.status)]} 流转为 ${STATUS_LABEL[target]}？流转不可回退。`,
    okText: '确认流转',
    cancelText: '取消',
    async onOk() {
      try {
        await transitionCycle(record.id, target);
        message.success(`已流转为 ${STATUS_LABEL[target]}`);
        await load();
      } catch (e) {
        message.error(errorText(e, '状态流转失败'));
      }
    },
  });
}

function handleDelete(record: Cycle) {
  if (norm(record.status) !== 'DRAFT') return message.warning('仅草稿周期可删除');
  Modal.confirm({
    title: '确认删除',
    content: `将删除周期「${record.periodCode}」，删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteCycle(record.id);
        message.success('已删除');
        await load();
      } catch (e) {
        message.error(errorText(e, '删除失败'));
      }
    },
  });
}

onMounted(load);
</script>

<template>
  <div class="cycle-page">
    <section class="head">
      <div>
        <h2 class="head-title">考核周期</h2>
        <p class="head-sub">四态单向流转：草稿 → 进行中 → 评分中 → 已结束；编辑与删除仅草稿可用</p>
      </div>
      <a-space>
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建周期</a-button>
      </a-space>
    </section>

    <a-card :bordered="false" class="table-card">
      <a-table
        row-key="id"
        :data-source="rows"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 960 }"
        @change="onTableChange"
      >
        <a-table-column key="periodCode" title="周期编码" data-index="periodCode" :width="150" />
        <a-table-column key="planId" title="方案 ID" data-index="planId" :width="110" />
        <a-table-column key="startDate" title="开始日期" data-index="startDate" :width="130" />
        <a-table-column key="endDate" title="结束日期" data-index="endDate" :width="130" />
        <a-table-column key="status" title="状态" :width="110">
          <template #default="{ record }">
            <a-tag :color="STATUS_COLOR[norm(record.status)]">
              {{ STATUS_LABEL[norm(record.status)] ?? record.status }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column key="createdAt" title="创建时间" :width="170">
          <template #default="{ record }">
            {{ record.createdAt ? String(record.createdAt).replace('T', ' ').slice(0, 16) : '—' }}
          </template>
        </a-table-column>
        <a-table-column key="action" title="操作" :width="230" fixed="right">
          <template #default="{ record }">
            <a-button
              v-if="canWrite && norm(record.status) === 'DRAFT'"
              type="link"
              size="small"
              @click="openEdit(record)"
            >编辑</a-button>
            <a-button
              v-if="canWrite && NEXT_TARGET[norm(record.status)]"
              type="link"
              size="small"
              @click="confirmTransition(record)"
            >
              <template #icon><SwapRightOutlined /></template>
              流转
            </a-button>
            <a-button
              v-if="canWrite && norm(record.status) === 'DRAFT'"
              type="link"
              size="small"
              danger
              @click="handleDelete(record)"
            >删除</a-button>
          </template>
        </a-table-column>
        <template #emptyText>
          <EmptyHint description="暂无考核周期" hint="点击右上角「新建周期」创建第一个周期" />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="modalTitle"
      :confirm-loading="submitting"
      :mask-closable="false"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="方案 ID" required>
          <a-input-number v-model:value="form.planId" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="周期编码" required>
          <a-input v-model:value="form.periodCode" placeholder="如 2026-H2" />
        </a-form-item>
        <a-form-item label="开始日期" required>
          <a-date-picker v-model:value="form.startDate" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
        <a-form-item label="结束日期" required>
          <a-date-picker v-model:value="form.endDate" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
        <a-alert type="info" show-icon message="状态由「流转」按钮驱动，新建固定为草稿，编辑不可改状态。" />
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.cycle-page { display: flex; flex-direction: column; gap: var(--space-6); }
.head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-4); }
.head-title { margin: 0; font-size: 20px; font-weight: 600; color: var(--color-text-primary); }
.head-sub { margin: var(--space-2) 0 0; font-size: 14px; color: var(--color-text-secondary); }
.table-card { border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
</style>
