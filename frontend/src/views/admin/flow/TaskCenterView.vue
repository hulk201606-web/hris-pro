<script setup lang="ts">
/**
 * 管理端-待办中心：跨流程统一视图（待办 / 已办 / 抄送 / 我发起 / 无主任务）。
 *
 * 各 Tab 的数据源只是不同端点，表格列一致，因此共用一个表格与一套分页状态。
 * 待办 Tab 额外提供任务操作（同意/驳回/转办/加签），「我发起」提供撤回，
 * 「无主任务」提供认领与指派（指派仅 flow:task:write 可见）。
 * 所有操作成功后会重新拉取当前 Tab，避免本地状态与服务端不一致。
 */
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import {
  assignTask,
  claimTask,
  fetchCcTasks,
  fetchDoneTasks,
  fetchStartedTasks,
  fetchTodoTasks,
  fetchUnassignedTasks,
} from '../../../api/flow';
import type { PageResult, TaskView } from '../../../types/api';
import { useUserStore } from '../../../stores/user';
import TaskActionModal from '../../../components/admin/flow/TaskActionModal.vue';
import type { TaskAction } from '../../../components/admin/flow/TaskActionModal.vue';
import TrackModal from '../../../components/admin/flow/TrackModal.vue';

type TabKey = 'todo' | 'done' | 'cc' | 'started' | 'unassigned';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'todo', label: '我的待办' },
  { key: 'done', label: '我的已办' },
  { key: 'cc', label: '抄送我的' },
  { key: 'started', label: '我发起的' },
  { key: 'unassigned', label: '无主任务' },
];

const userStore = useUserStore();
const activeTab = ref<TabKey>('todo');
const rows = ref<TaskView[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const loading = ref(false);

const actionOpen = ref(false);
const trackOpen = ref(false);
const currentAction = ref<TaskAction | null>(null);
const currentTask = ref<TaskView | null>(null);
const currentInstance = ref<string | null>(null);

const assignOpen = ref(false);
const assignTarget = ref<TaskView | null>(null);
const assignUserId = ref<number | undefined>(undefined);
const assignSubmitting = ref(false);

const FETCHERS: Record<TabKey, (p: number, s: number) => Promise<PageResult<TaskView>>> = {
  todo: fetchTodoTasks,
  done: fetchDoneTasks,
  cc: fetchCcTasks,
  started: fetchStartedTasks,
  unassigned: (p, s) => fetchUnassignedTasks(p, s),
};

const canOperate = computed(() => activeTab.value === 'todo');
const canWithdraw = computed(() => activeTab.value === 'started');
const canDispatch = computed(
  () => activeTab.value === 'unassigned' && userStore.hasPerm('flow:task:write'),
);

async function load() {
  loading.value = true;
  try {
    const res = await FETCHERS[activeTab.value](page.value, size.value);
    rows.value = res.records ?? [];
    total.value = res.total ?? 0;
  } catch (e) {
    message.error(e instanceof Error ? e.message : '任务加载失败');
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function switchTab(key: TabKey) {
  activeTab.value = key;
  page.value = 1;
  load();
}

function onPageChange(p: number, s: number) {
  page.value = p;
  size.value = s;
  load();
}

function openAction(task: TaskView, action: TaskAction) {
  currentTask.value = task;
  currentAction.value = action;
  actionOpen.value = true;
}

function openTrack(task: TaskView) {
  currentInstance.value = task.instanceId;
  trackOpen.value = true;
}

async function handleClaim(task: TaskView) {
  if (!task.taskId) return message.error('该任务缺少任务 ID，无法认领');
  try {
    await claimTask(task.taskId);
    message.success('认领成功，任务已进入我的待办');
    load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '认领失败');
  }
}

function openAssign(task: TaskView) {
  assignTarget.value = task;
  assignUserId.value = undefined;
  assignOpen.value = true;
}

async function handleAssign() {
  const task = assignTarget.value;
  if (!task?.taskId) return message.error('该任务缺少任务 ID，无法指派');
  if (!assignUserId.value) return message.warning('请填写目标用户 ID');
  assignSubmitting.value = true;
  try {
    await assignTask(task.taskId, assignUserId.value);
    message.success('指派成功');
    assignOpen.value = false;
    load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '指派失败');
  } finally {
    assignSubmitting.value = false;
  }
}

function fmt(t?: string): string {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-';
}

onMounted(load);
</script>

<template>
  <div class="task-center">
    <section class="head">
      <h2 class="head-title">待办中心</h2>
      <p class="head-sub">
        跨流程统一视图；任务操作不会直接改业务单据状态，业务状态由流程事件回写。
      </p>
    </section>

    <a-tabs :active-key="activeTab" @change="(k: string | number) => switchTab(k as TabKey)">
      <a-tab-pane v-for="t in TABS" :key="t.key" :tab="t.label" />
    </a-tabs>

    <a-table
      :data-source="rows"
      :loading="loading"
      row-key="taskId"
      size="middle"
      :pagination="{
        current: page,
        pageSize: size,
        total,
        showSizeChanger: true,
        showTotal: (n: number) => `共 ${n} 条`,
        onChange: onPageChange,
      }"
      :locale="{ emptyText: '暂无任务' }"
    >
      <a-table-column v-if="activeTab === 'unassigned'" key="taskId" title="任务 ID" data-index="taskId" :width="200" />
      <a-table-column key="nodeName" title="节点" data-index="nodeName" :width="150" />
      <a-table-column key="bizType" title="业务类型" data-index="bizType" :width="130" />
      <a-table-column key="bizKey" title="业务键" data-index="bizKey" :width="200" />
      <a-table-column key="procDefKey" title="流程定义" data-index="procDefKey" :width="170" />
      <a-table-column key="status" title="状态" data-index="status" :width="110" />
      <a-table-column key="createTime" title="创建时间" :width="170">
        <template #default="{ record }">{{ fmt(record.createTime) }}</template>
      </a-table-column>
      <a-table-column key="actions" title="操作" :width="220" fixed="right">
        <template #default="{ record }">
          <template v-if="canOperate">
            <a-button type="link" size="small" @click="openAction(record, 'approve')">同意</a-button>
            <a-button type="link" size="small" danger @click="openAction(record, 'reject')">驳回</a-button>
            <a-button type="link" size="small" @click="openAction(record, 'transfer')">转办</a-button>
            <a-button type="link" size="small" @click="openAction(record, 'addSign')">加签</a-button>
          </template>
          <template v-else-if="canDispatch">
            <a-button type="link" size="small" @click="handleClaim(record)">认领</a-button>
            <a-button type="link" size="small" @click="openAssign(record)">指派</a-button>
          </template>
          <a-button
            v-else-if="canWithdraw"
            type="link"
            size="small"
            @click="openAction(record, 'withdraw')"
          >
            撤回
          </a-button>
          <a-button type="link" size="small" @click="openTrack(record)">轨迹</a-button>
        </template>
      </a-table-column>
    </a-table>

    <TaskActionModal
      v-model:open="actionOpen"
      :action="currentAction"
      :task="currentTask"
      @done="load"
    />
    <TrackModal v-model:open="trackOpen" :instance-id="currentInstance" />

    <a-modal
      :open="assignOpen"
      title="指派任务"
      :confirm-loading="assignSubmitting"
      ok-text="确认指派"
      cancel-text="取消"
      @update:open="assignOpen = $event"
      @ok="handleAssign"
    >
      <a-descriptions v-if="assignTarget" :column="1" size="small" bordered class="brief">
        <a-descriptions-item label="节点">{{ assignTarget.nodeName }}</a-descriptions-item>
        <a-descriptions-item label="业务键">{{ assignTarget.bizKey || '-' }}</a-descriptions-item>
      </a-descriptions>
      <a-form layout="vertical">
        <a-form-item label="目标用户 ID" required>
          <a-input-number
            v-model:value="assignUserId"
            :min="1"
            :precision="0"
            placeholder="目标用户 ID"
            style="width: 100%"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.task-center {
  padding: 4px;
}
.head {
  margin-bottom: 12px;
}
.brief {
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
</style>
