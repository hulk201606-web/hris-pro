<script setup lang="ts">
/**
 * 管理端-评分记录（评估结果）：按视角展示 / 录入过程分。
 *
 * 契约要点（后端 EvaluationService + ScoreVisibilityRule）：
 * - scores/comment 为 null 表示**当前视角无权查看**（互见掩码），不是「未评分」；
 *   此类行只展示说明文案，绝不回填表单、不允许编辑；
 * - 评分写入受周期状态机守卫，错误均为 PRF_ERROR(55000)，需按消息区分三类窗口：
 *   自评窗口关闭 / 周期未启动 / 周期已关闭。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import {
  createEvaluation,
  listEvaluations,
  updateEvaluation,
} from '../../../api/performance';
import { BizError } from '../../../api/error';
import { useUserStore } from '../../../stores/user';
import type { EvaluationRow } from '../../../types/api-batch9';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('prf:evaluation:write'));

const rows = ref<EvaluationRow[]>([]);
const loading = ref(false);
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });

const modalOpen = ref(false);
const editingId = ref<number | null>(null);
const submitting = ref(false);
const form = reactive({ taskId: undefined as number | undefined, evaluatorId: undefined as number | undefined, scores: '', comment: '' });

/** 掩码判定：两个字段同时为 null 才视为「不可见」，单字段为空仍可能是正常录入 */
function masked(row: EvaluationRow): boolean {
  return row.scores === null && row.comment === null;
}

async function load() {
  loading.value = true;
  try {
    const page = await listEvaluations(pagination.current, pagination.pageSize);
    rows.value = page.records;
    pagination.total = page.total;
  } catch (e) {
    message.error(e instanceof BizError ? e.message : '评分列表加载失败');
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
  Object.assign(form, { taskId: undefined, evaluatorId: undefined, scores: '', comment: '' });
  modalOpen.value = true;
}

function openEdit(row: EvaluationRow) {
  if (masked(row)) return message.warning('当前视角不可见该评分，无法回填或修改');
  editingId.value = row.id;
  Object.assign(form, {
    taskId: row.taskId ?? undefined,
    evaluatorId: row.evaluatorId ?? undefined,
    scores: row.scores ?? '',
    comment: row.comment ?? '',
  });
  modalOpen.value = true;
}

/** 评分写入错误分类：按后端消息识别三种窗口，避免一律「业务处理失败」 */
function scoreError(e: unknown, fallback: string): string {
  const msg = e instanceof BizError ? e.message : '';
  if (msg.includes('自评窗口')) return `自评窗口已关闭：${msg}`;
  if (msg.includes('尚未启动')) return `周期未启动：${msg}`;
  if (msg.includes('已关闭')) return `周期已关闭：${msg}`;
  return msg || fallback;
}

async function handleSubmit() {
  if (!form.taskId || !form.evaluatorId) return message.warning('请填写任务 ID 与评价人 ID');
  if (!form.scores.trim() && !form.comment.trim()) return message.warning('评分与评语至少填写一项');
  submitting.value = true;
  try {
    const payload = {
      taskId: form.taskId,
      evaluatorId: form.evaluatorId,
      scores: form.scores.trim() || null,
      comment: form.comment.trim() || null,
    };
    if (editingId.value) {
      await updateEvaluation(editingId.value, payload);
      message.success('评分已更新');
    } else {
      await createEvaluation(payload);
      message.success('评分已提交');
    }
    modalOpen.value = false;
    await load();
  } catch (e) {
    message.error(scoreError(e, '评分提交失败'));
  } finally {
    submitting.value = false;
  }
}

function fmtTime(v?: string): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—';
}

onMounted(load);
</script>

<template>
  <div class="score-page">
    <section class="head">
      <div>
        <h2 class="head-title">评分记录</h2>
        <p class="head-sub">过程分按互见规则掩码；null 表示当前视角无权查看，不代表未评分</p>
      </div>
      <a-space>
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建评分</a-button>
      </a-space>
    </section>

    <a-alert
      type="info"
      show-icon
      class="notice"
      message="互见口径：自评分在自评窗口关闭后对上级/HR 可见；上级与互评分仅周期关闭（结果公布）后对被评人可见。"
    />

    <a-card :bordered="false" class="table-card">
      <a-table
        row-key="id"
        :data-source="rows"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1000 }"
        @change="onTableChange"
      >
        <a-table-column key="id" title="ID" data-index="id" :width="80" />
        <a-table-column key="taskId" title="任务 ID" :width="110">
          <template #default="{ record }">{{ record.taskId ?? '—' }}</template>
        </a-table-column>
        <a-table-column key="evaluatorId" title="评价人 ID" :width="110">
          <template #default="{ record }">{{ record.evaluatorId ?? '—' }}</template>
        </a-table-column>
        <a-table-column key="scores" title="评分" :width="260">
          <template #default="{ record }">
            <a-tag v-if="masked(record)" color="default">结果公布后可见 / 暂无权限</a-tag>
            <span v-else-if="record.scores">{{ record.scores }}</span>
            <span v-else class="muted">—</span>
          </template>
        </a-table-column>
        <a-table-column key="comment" title="评语">
          <template #default="{ record }">
            <span v-if="masked(record)" class="muted">不可见</span>
            <span v-else-if="record.comment">{{ record.comment }}</span>
            <span v-else class="muted">—</span>
          </template>
        </a-table-column>
        <a-table-column key="updatedAt" title="更新时间" :width="160">
          <template #default="{ record }">{{ fmtTime(record.updatedAt) }}</template>
        </a-table-column>
        <a-table-column key="action" title="操作" :width="110" fixed="right">
          <template #default="{ record }">
            <a-button
              v-if="canWrite && !masked(record)"
              type="link"
              size="small"
              @click="openEdit(record)"
            >编辑</a-button>
            <span v-else-if="masked(record)" class="muted">—</span>
          </template>
        </a-table-column>
        <template #emptyText>
          <EmptyHint description="暂无评分记录" hint="点击「新建评分」录入任务评分" />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑评分' : '新建评分'"
      :confirm-loading="submitting"
      :mask-closable="false"
      ok-text="提交"
      cancel-text="取消"
      @ok="handleSubmit"
    >
      <a-form layout="vertical" :model="form">
        <a-form-item label="任务 ID" required>
          <a-input-number v-model:value="form.taskId" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="评价人 ID" required>
          <a-input-number v-model:value="form.evaluatorId" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="评分">
          <a-textarea v-model:value="form.scores" :rows="3" placeholder="如 JSON 结构或分值说明" />
        </a-form-item>
        <a-form-item label="评语">
          <a-textarea v-model:value="form.comment" :rows="3" placeholder="选填" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.score-page {
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
.notice,
.table-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
.muted {
  color: var(--color-text-muted);
}
</style>
