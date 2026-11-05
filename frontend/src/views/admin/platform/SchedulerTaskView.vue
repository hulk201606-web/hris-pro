<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import SchedulerLogPanel from '../../../components/admin/platform/SchedulerLogPanel.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { useUserStore } from '../../../stores/user';
import {
  createSchedulerTask,
  listSchedulerHandlers,
  listSchedulerTasks,
  runSchedulerTask,
  updateSchedulerTask,
} from '../../../api/scheduler';
import type { HandlerOption, SchedulerTask } from '../../../api/scheduler';

/**
 * 管理端-任务调度看板（PLT-F-011）。
 * 注册项与执行日志同屏：只看到「任务已启用」不够，必须能看到
 * 「最近一次到底跑没跑、失败原因是什么、重试了几次」。
 */
const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('plt:scheduler:write'));

const tasks = ref<SchedulerTask[]>([]);
const handlers = ref<HandlerOption[]>([]);
const loading = ref(false);
const logPanel = ref<InstanceType<typeof SchedulerLogPanel> | null>(null);

const taskColumns = computed<any[]>(() => [
  { title: '任务键', dataIndex: 'taskKey', key: 'taskKey', width: 180 },
  { title: 'cron', dataIndex: 'cron', key: 'cron', width: 140 },
  { title: '处理器', dataIndex: 'handler', key: 'handler', width: 210 },
  { title: '状态', key: 'status', width: 100 },
  { title: '装载', key: 'scheduled', width: 100 },
  { title: '操作', key: 'action', width: 210, fixed: 'right' },
]);

async function loadTasks() {
  loading.value = true;
  try {
    tasks.value = await listSchedulerTasks();
  } catch (e) {
    message.error(errorText(e, '调度任务加载失败'));
  } finally {
    loading.value = false;
  }
}

async function loadHandlers() {
  try {
    handlers.value = await listSchedulerHandlers();
  } catch (e) {
    message.error(errorText(e, '处理器列表加载失败'));
  }
}

function refreshAll() {
  loadTasks();
  logPanel.value?.reload();
}

onMounted(() => {
  loadTasks();
  loadHandlers();
});

/* ------------------------------ 新建 / 编辑 ------------------------------ */

const modalOpen = ref(false);
const editingId = ref<number | null>(null);
const submitting = ref(false);
const formState = reactive<Record<string, any>>({});

function openCreate() {
  Object.keys(formState).forEach((k) => delete formState[k]);
  formState.status = 1;
  formState.handler = handlers.value[0]?.key ?? '';
  editingId.value = null;
  modalOpen.value = true;
}

function openEdit(record: SchedulerTask) {
  Object.keys(formState).forEach((k) => delete formState[k]);
  formState.taskKey = record.taskKey;
  formState.cron = record.cron;
  formState.handler = record.handler;
  formState.status = record.status;
  editingId.value = record.id;
  modalOpen.value = true;
}

async function submit() {
  if (!formState.taskKey || !formState.cron || !formState.handler) {
    message.warning('请填写「任务键」「cron」「处理器」');
    return;
  }
  const payload: Record<string, unknown> = {
    taskKey: formState.taskKey,
    cron: formState.cron,
    handler: formState.handler,
    status: formState.status,
  };
  submitting.value = true;
  try {
    if (editingId.value === null) {
      await createSchedulerTask(payload);
      message.success('创建成功');
    } else {
      await updateSchedulerTask(editingId.value, payload);
      message.success('保存成功');
    }
    modalOpen.value = false;
    await loadTasks();
  } catch (e) {
    message.error(errorText(e, '保存失败'));
  } finally {
    submitting.value = false;
  }
}

async function toggle(record: SchedulerTask) {
  try {
    await updateSchedulerTask(record.id, {
      taskKey: record.taskKey,
      cron: record.cron,
      handler: record.handler,
      status: record.status === 1 ? 0 : 1,
    });
    message.success(record.status === 1 ? '已停用' : '已启用');
    await loadTasks();
  } catch (e) {
    message.error(errorText(e, '状态切换失败'));
  }
}

async function runNow(record: SchedulerTask) {
  try {
    const res = await runSchedulerTask(record.id);
    if (res.status === 'SUCCESS') {
      message.success(`${res.taskKey}: ${res.message}`);
    } else {
      message.warning(`${res.taskKey}: ${res.message}`);
    }
    logPanel.value?.reload();
  } catch (e) {
    message.error(errorText(e, '立即执行失败'));
  }
}
</script>

<template>
  <div class="scheduler-page">
    <section class="head">
      <div>
        <h2 class="head-title">任务调度</h2>
        <p class="head-sub">按 cron 装载租户任务，支持启停、立即执行、失败重试与执行日志</p>
      </div>
      <a-space>
        <a-button @click="refreshAll">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建任务</a-button>
      </a-space>
    </section>

    <a-alert
      type="info"
      show-icon
      message="运营配置指引：组织版本到期激活"
      description="新建任务时 taskKey 填 ORG_VERSION_ACTIVATE_DUE，cron 设为每日一次（如 0 30 1 * * *），处理器选择组织版本到期激活。若处理器下拉中没有该项，说明后端尚未注册该 handler，需先在平台侧注册后再配置；草稿版本也可在组织版本页手动激活。"
    />

    <a-card :bordered="false" class="panel">
      <a-table
        row-key="id"
        :columns="taskColumns"
        :data-source="tasks"
        :loading="loading"
        :pagination="{ pageSize: 10 }"
        :scroll="{ x: 940 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : 'default'">
              {{ record.status === 1 ? '启用' : '停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'scheduled'">
            <a-tag :color="record.scheduled ? 'blue' : 'orange'">
              {{ record.scheduled ? '已装载' : '未装载' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="canWrite">
              <a-button type="link" size="small" @click="runNow(record)">立即执行</a-button>
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm
                :title="record.status === 1 ? '停用后不再按 cron 触发，确认？' : '启用后将重新装载，确认？'"
                @confirm="toggle(record)"
              >
                <a-button type="link" size="small">{{ record.status === 1 ? '停用' : '启用' }}</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
        <template #emptyText>
          <EmptyHint
            description="暂无调度任务"
            :hint="canWrite ? '点击右上角「新建任务」注册第一个定时任务' : ''"
          />
        </template>
      </a-table>
    </a-card>

    <SchedulerLogPanel ref="logPanel" />

    <a-modal
      v-model:open="modalOpen"
      :title="editingId === null ? '新建调度任务' : '编辑调度任务'"
      :confirm-loading="submitting"
      :mask-closable="false"
      ok-text="保存"
      cancel-text="取消"
      @ok="submit"
    >
      <a-form :model="formState" layout="vertical">
        <a-form-item label="任务键" required>
          <a-input v-model:value="formState.taskKey" placeholder="如 EXPORT_DISPATCH" :disabled="editingId !== null" />
        </a-form-item>
        <a-form-item label="cron 表达式" required>
          <a-input v-model:value="formState.cron" placeholder="如 0 */5 * * * *（Spring 6 字段格式）" />
        </a-form-item>
        <a-form-item label="处理器" required>
          <a-select
            v-model:value="formState.handler"
            :options="handlers.map((h) => ({ label: h.description, value: h.key }))"
          />
        </a-form-item>
        <a-form-item label="状态">
          <a-select
            v-model:value="formState.status"
            :options="[{ label: '启用', value: 1 }, { label: '停用', value: 0 }]"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.scheduler-page {
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
</style>
