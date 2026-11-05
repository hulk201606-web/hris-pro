<script setup lang="ts">
/**
 * 管理端-算薪监控页（CMP-F-005/006）：选择核算批次后执行计算并监控。
 * 「执行计算」走缺省异步（批 8：calculate 无 mode 参数即异步，run 置 CALCULATING），
 * 每 3 秒轮询进度（上限 30 次）直至完成；任务展示区见 CalcMonitorPanel 组件。
 * 权限：cmp:payroll-run:read/write。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { CaretRightOutlined, ReloadOutlined } from '@ant-design/icons-vue';
import CalcMonitorPanel from '../../../components/admin/CalcMonitorPanel.vue';
import PayrollOpsActions from '../../../components/admin/compensation/PayrollOpsActions.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import {
  calculatePayrollRun,
  getCalcProgress,
  listCalcTasks,
  listPayrollRuns,
  retryCalcTask,
} from '../../../api/compensation';
import { useUserStore } from '../../../stores/user';
import type { PayrollRun } from '../../../types/admin';
import type { CalcProgress, CalcTask } from '../../../types/api-batch3';

const userStore = useUserStore();
const canRead = computed(() => userStore.hasPerm('cmp:payroll-run:read'));
const canWrite = computed(() => userStore.hasPerm('cmp:payroll-run:write'));

const POLL_INTERVAL_MS = 3000;
const POLL_MAX_TIMES = 30;

const runs = ref<PayrollRun[]>([]);
const runId = ref<number | null>(null);
const selectedRun = computed(() => runs.value.find((r) => r.id === runId.value) ?? null);
const progress = ref<CalcProgress | null>(null);
const tasks = ref<CalcTask[]>([]);
const statusFilter = ref<string | undefined>(undefined);
const loadingTasks = ref(false);
const calculating = ref(false);
const retrying = reactive<Record<number, boolean>>({});

let pollTimer: ReturnType<typeof setInterval> | null = null;
let pollCount = 0;

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

/** 每 3 秒轮询进度，直至任务全部落定或达到 30 次上限 */
function startPolling() {
  stopPolling();
  pollCount = 0;
  pollTimer = setInterval(async () => {
    pollCount += 1;
    if (pollCount > POLL_MAX_TIMES) {
      stopPolling();
      message.warning('已达轮询上限（30 次），请手动刷新查看最新进度');
      return;
    }
    try {
      const p = await getCalcProgress(runId.value as number);
      progress.value = p;
      if (p.total > 0 && p.success + p.failed + p.skipped >= p.total) {
        stopPolling();
        if (p.failed > 0) message.warning(`计算结束：成功 ${p.success}，失败 ${p.failed}，可对失败员工单人重算`);
        else message.success(`计算完成：成功 ${p.success}，跳过 ${p.skipped}`);
        await loadTasks();
      }
    } catch {
      // 轮询期单次失败不打断，达上限后自动停止
    }
  }, POLL_INTERVAL_MS);
}

async function loadRuns() {
  if (!canRead.value) return;
  try {
    const page = await listPayrollRuns(1, 100);
    runs.value = page.records;
  } catch (e) {
    message.error(errorText(e, '核算批次列表加载失败'));
  }
}

/** 执行计算：确认弹窗后触发 calculate（缺省异步，后端 run 置 CALCULATING），随后进入 3 秒轮询 */
function confirmCalculate() {
  if (!runId.value) {
    message.warning('请先选择核算批次');
    return;
  }
  Modal.confirm({
    title: '确认执行计算',
    content: `将对批次 #${runId.value} 异步触发逐员工算薪（批次置为「计算中」），进度自动轮询刷新，确认继续吗？`,
    okText: '执行计算',
    cancelText: '取消',
    onOk: async () => {
      calculating.value = true;
      try {
        // 异步快照响应：runId + CALCULATING 状态 + 全 0 进度，真实进度由轮询获取
        const resp = await calculatePayrollRun(runId.value as number);
        progress.value = {
          runId: resp.runId,
          total: resp.total,
          success: resp.success,
          failed: resp.failed,
          skipped: resp.skipped,
          progressPercent:
            resp.total > 0
              ? Math.round(((resp.success + resp.failed + resp.skipped) / resp.total) * 100)
              : 0,
        };
        message.info(`计算已异步触发（批次状态 ${resp.status}），进度将自动刷新`);
        startPolling();
        await Promise.all([loadTasks(), loadRuns()]);
      } catch (e) {
        message.error(errorText(e, '执行计算失败'));
      } finally {
        calculating.value = false;
      }
    },
  });
}

async function selectRun() {
  stopPolling();
  progress.value = null;
  if (!runId.value) {
    tasks.value = [];
    return;
  }
  try {
    progress.value = await getCalcProgress(runId.value);
  } catch {
    progress.value = null;
  }
  await loadTasks();
}

/** 复核/对比等操作完成后同时刷新批次状态与当前批次进度 */
async function refreshAfterOps() {
  await loadRuns();
  await selectRun();
}

async function loadTasks() {
  if (!canRead.value || !runId.value) return;
  loadingTasks.value = true;
  try {
    tasks.value = await listCalcTasks(runId.value, statusFilter.value);
  } catch (e) {
    message.error(errorText(e, '任务列表加载失败'));
    tasks.value = [];
  } finally {
    loadingTasks.value = false;
  }
}

async function handleRetry(record: CalcTask) {
  if (!runId.value) return;
  retrying[record.id] = true;
  try {
    await retryCalcTask(runId.value, record.empId);
    message.success(`员工 ${record.empId} 重算成功`);
    await loadTasks();
    progress.value = await getCalcProgress(runId.value);
  } catch (e) {
    message.error(errorText(e, '单人重算失败'));
  } finally {
    retrying[record.id] = false;
  }
}

onMounted(loadRuns);
onBeforeUnmount(stopPolling);
</script>

<template>
  <div class="calc-page">
    <section class="head">
      <div>
        <h2 class="head-title">算薪监控</h2>
        <p class="head-sub">选择核算批次执行计算，跟踪逐员工任务进度；失败员工可单人重算</p>
      </div>
      <a-space>
        <PayrollOpsActions :run="selectedRun" @done="refreshAfterOps" />
        <a-button @click="selectRun">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button
          v-if="canWrite"
          type="primary"
          danger
          :loading="calculating"
          :disabled="!runId"
          @click="confirmCalculate"
        >
          <template #icon><CaretRightOutlined /></template>
          执行计算
        </a-button>
      </a-space>
    </section>

    <a-card :bordered="false" class="filter-card">
      <a-form layout="inline" class="filter-form">
        <a-form-item label="核算批次">
          <a-select
            v-model:value="runId"
            style="width: 320px"
            placeholder="选择批次（周期 / 薪酬组）"
            show-search
            option-filter-prop="label"
            :options="runs.map((r) => ({ label: `#${r.id} ${r.periodCode}（薪酬组 ${r.payGroupId}）`, value: r.id }))"
            @change="selectRun"
          />
        </a-form-item>
        <a-form-item label="任务状态">
          <a-select
            v-model:value="statusFilter"
            style="width: 140px"
            placeholder="全部"
            allow-clear
            :options="[
              { label: '全部', value: '' },
              { label: '成功', value: 'SUCCESS' },
              { label: '失败', value: 'FAILED' },
              { label: '跳过', value: 'SKIPPED' },
            ]"
            @change="loadTasks"
          />
        </a-form-item>
      </a-form>
    </a-card>

    <CalcMonitorPanel
      :progress="progress"
      :tasks="tasks"
      :loading="loadingTasks"
      :can-write="canWrite"
      :has-run-selected="Boolean(runId)"
      :retrying="retrying"
      @retry="handleRetry"
    />
  </div>
</template>

<style scoped>
.calc-page {
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

.filter-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.filter-form {
  row-gap: var(--space-3);
}
</style>
