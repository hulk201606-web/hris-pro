<script setup lang="ts">
/**
 * 管理端-待办中心：跨流程统一视图（待办 / 已办 / 抄送 / 我发起）。
 *
 * 四个 Tab 的数据源只是不同端点，表格列一致，因此共用一个表格与一套分页状态。
 * 待办 Tab 额外提供任务操作（同意/驳回/转办/加签），「我发起」提供撤回。
 * 所有操作成功后会重新拉取当前 Tab，避免本地状态与服务端不一致。
 */
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import {
  fetchCcTasks,
  fetchDoneTasks,
  fetchStartedTasks,
  fetchTodoTasks,
} from '../../../api/flow';
import type { PageResult, TaskView } from '../../../types/api';
import TaskActionModal from '../../../components/admin/flow/TaskActionModal.vue';
import type { TaskAction } from '../../../components/admin/flow/TaskActionModal.vue';
import TrackModal from '../../../components/admin/flow/TrackModal.vue';

type TabKey = 'todo' | 'done' | 'cc' | 'started';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'todo', label: '我的待办' },
  { key: 'done', label: '我的已办' },
  { key: 'cc', label: '抄送我的' },
  { key: 'started', label: '我发起的' },
];

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

const FETCHERS: Record<TabKey, (p: number, s: number) => Promise<PageResult<TaskView>>> = {
  todo: fetchTodoTasks,
  done: fetchDoneTasks,
  cc: fetchCcTasks,
  started: fetchStartedTasks,
};

const canOperate = computed(() => activeTab.value === 'todo');
const canWithdraw = computed(() => activeTab.value === 'started');

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
  </div>
</template>

<style scoped>
.task-center {
  padding: 4px;
}
.head {
  margin-bottom: 12px;
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
