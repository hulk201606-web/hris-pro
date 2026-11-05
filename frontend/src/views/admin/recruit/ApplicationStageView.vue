<script setup lang="ts">
/**
 * 管理端-投递阶段流转（RCS-F-012）：对 /rcu/applications 做初筛通过 / 淘汰。
 *
 * 契约要点（后端 ApplicationService.advance）：
 * - action=PASS：可推进 toStage（APPLIED/SCREEN/INTERVIEW/OFFER/HIRED），并为候选人幂等打标签；
 * - action=REJECT：reason 必填且属枚举，OTHER 必须填 note；状态置 REJECTED 并写 rcu_stage_log。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { advanceApplication, listApplications } from '../../../api/recruit';
import { useUserStore } from '../../../stores/user';
import type { ApplicationRow } from '../../../types/api-batch9';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('rcu:application:write'));

const STAGES = [
  { label: '待筛选', value: 'APPLIED' },
  { label: '初筛', value: 'SCREEN' },
  { label: '面试', value: 'INTERVIEW' },
  { label: 'Offer', value: 'OFFER' },
  { label: '已录用', value: 'HIRED' },
];
/** 与后端 RejectReason 枚举一致 */
const REASONS = [
  { label: '技能不匹配', value: 'SKILL_MISMATCH' },
  { label: '薪资不匹配', value: 'SALARY_MISMATCH' },
  { label: '态度/意愿不符', value: 'ATTITUDE' },
  { label: '编制关闭', value: 'HEADCOUNT_CLOSED' },
  { label: '候选人主动撤回', value: 'WITHDRAWN' },
  { label: '其他（需填写说明）', value: 'OTHER' },
];

const rows = ref<ApplicationRow[]>([]);
const loading = ref(false);
const pagination = reactive({ current: 1, pageSize: 10, total: 0 });

const passOpen = ref(false);
const rejectOpen = ref(false);
const submitting = ref(false);
const current = ref<ApplicationRow | null>(null);
const passForm = reactive({ toStage: 'SCREEN', tags: [] as string[] });
const rejectForm = reactive({ reason: undefined as string | undefined, note: '' });

const noteRequired = computed(() => rejectForm.reason === 'OTHER');

async function load() {
  loading.value = true;
  try {
    const page = await listApplications(pagination.current, pagination.pageSize);
    rows.value = page.records;
    pagination.total = page.total;
  } catch (e) {
    message.error(errorText(e, '投递列表加载失败'));
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

function openPass(row: ApplicationRow) {
  current.value = row;
  passForm.toStage = row.stage || 'SCREEN';
  passForm.tags = [];
  passOpen.value = true;
}

function openReject(row: ApplicationRow) {
  current.value = row;
  rejectForm.reason = undefined;
  rejectForm.note = '';
  rejectOpen.value = true;
}

async function submitPass() {
  if (!current.value) return;
  submitting.value = true;
  try {
    await advanceApplication(current.value.id, {
      action: 'PASS',
      toStage: passForm.toStage,
      tags: passForm.tags,
    });
    message.success('已通过并推进阶段');
    passOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, '通过失败'));
  } finally {
    submitting.value = false;
  }
}

async function submitReject() {
  if (!current.value) return;
  if (!rejectForm.reason) return message.warning('淘汰必须选择原因');
  if (noteRequired.value && !rejectForm.note.trim()) return message.warning('原因为「其他」时必须填写说明');
  submitting.value = true;
  try {
    await advanceApplication(current.value.id, {
      action: 'REJECT',
      reason: rejectForm.reason,
      note: rejectForm.note.trim() || undefined,
    });
    message.success('已淘汰并留痕');
    rejectOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, '淘汰失败'));
  } finally {
    submitting.value = false;
  }
}

function fmt(v?: string): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—';
}

onMounted(load);
</script>

<template>
  <div class="app-page">
    <section class="head">
      <div>
        <h2 class="head-title">投递阶段</h2>
        <p class="head-sub">初筛通过可推进阶段并打标签；淘汰必须选择结构化原因，OTHER 需补充说明</p>
      </div>
      <a-button @click="load">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </section>

    <a-card :bordered="false" class="table-card">
      <a-table
        row-key="id"
        :data-source="rows"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1000 }"
        @change="onTableChange"
      >
        <a-table-column key="id" title="投递 ID" data-index="id" :width="100" />
        <a-table-column key="candidateId" title="候选人 ID" data-index="candidateId" :width="110" />
        <a-table-column key="openingId" title="职位 ID" data-index="openingId" :width="110" />
        <a-table-column key="stage" title="阶段" :width="110">
          <template #default="{ record }">
            <a-tag>{{ STAGES.find((s) => s.value === record.stage)?.label ?? record.stage }}</a-tag>
          </template>
        </a-table-column>
        <a-table-column key="status" title="状态" :width="110">
          <template #default="{ record }">
            <a-tag :color="record.status === 'REJECTED' ? 'red' : 'green'">{{ record.status }}</a-tag>
          </template>
        </a-table-column>
        <a-table-column key="reason" title="淘汰原因" :width="160">
          <template #default="{ record }">
            {{ record.reason ? `${record.reason}${record.reasonNote ? ` / ${record.reasonNote}` : ''}` : '—' }}
          </template>
        </a-table-column>
        <a-table-column key="updatedAt" title="更新时间" :width="160">
          <template #default="{ record }">{{ fmt(record.updatedAt) }}</template>
        </a-table-column>
        <a-table-column key="action" title="操作" :width="150" fixed="right">
          <template #default="{ record }">
            <template v-if="canWrite">
              <a-button
                type="link"
                size="small"
                :disabled="record.status === 'REJECTED'"
                @click="openPass(record)"
              >通过</a-button>
              <a-button
                type="link"
                size="small"
                danger
                :disabled="record.status === 'REJECTED'"
                @click="openReject(record)"
              >淘汰</a-button>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </a-table-column>
        <template #emptyText>
          <EmptyHint description="暂无投递记录" hint="投递由招聘渠道或官网职位申请产生" />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="passOpen"
      title="初筛通过"
      :confirm-loading="submitting"
      ok-text="确认通过"
      cancel-text="取消"
      @ok="submitPass"
    >
      <a-form layout="vertical">
        <a-form-item label="推进至阶段">
          <a-select v-model:value="passForm.toStage" :options="STAGES" />
        </a-form-item>
        <a-form-item label="候选人标签">
          <a-select
            v-model:value="passForm.tags"
            mode="tags"
            :options="[]"
            placeholder="输入后回车添加，如 高潜/可立即到岗"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="rejectOpen"
      title="淘汰候选人"
      :confirm-loading="submitting"
      ok-text="确认淘汰"
      ok-type="danger"
      cancel-text="取消"
      @ok="submitReject"
    >
      <a-form layout="vertical">
        <a-form-item label="淘汰原因" required>
          <a-select v-model:value="rejectForm.reason" :options="REASONS" placeholder="请选择原因" />
        </a-form-item>
        <a-form-item label="补充说明" :required="noteRequired">
          <a-textarea
            v-model:value="rejectForm.note"
            :rows="3"
            :placeholder="noteRequired ? '原因为「其他」时必填' : '选填'"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.app-page { display: flex; flex-direction: column; gap: var(--space-6); }
.head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-4); }
.head-title { margin: 0; font-size: 20px; font-weight: 600; color: var(--color-text-primary); }
.head-sub { margin: var(--space-2) 0 0; font-size: 14px; color: var(--color-text-secondary); }
.table-card { border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.muted { color: var(--color-text-muted); }
</style>
