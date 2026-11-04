<script setup lang="ts">
/**
 * 管理端-导出记录：报表定义的异步导出任务列表，并可从本页发起新导出。
 *
 * 补全「报表定义 → 发起导出 → 查看结果」这条链路：此前只有定义维护页，
 * 导出任务无法在界面发起，也看不到生成的文件。
 *
 * 状态含义：PENDING 排队中 / DONE 已生成（fileId 可用）/ FAILED 失败。
 * 导出是数据外流路径，服务端要求独立的 plt:report:export 权限，与读写分开；
 * 因此本页的「发起导出」按钮由该权限点单独控制。
 */
import { onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { createExport, listExports, listReportDefs } from '../../../api/platform';
import type { ExportTaskResponse } from '../../../api/platform';
import type { ReportDefResponse } from '../../../types/api';
import { useUserStore } from '../../../stores/user';

const userStore = useUserStore();
const rows = ref<ExportTaskResponse[]>([]);
const defs = ref<ReportDefResponse[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const statusFilter = ref<string | undefined>(undefined);
const loading = ref(false);
const submitting = ref(false);
const selectedDefId = ref<number | undefined>(undefined);

const canExport = userStore.hasPerm('plt:report:export');

const STATUS_MAP: Record<string, string> = { PENDING: '排队中', DONE: '已完成', FAILED: '失败' };

async function loadDefs() {
  try {
    defs.value = await listReportDefs();
  } catch {
    // 定义列表加载失败不影响查看历史导出记录
  }
}

async function load() {
  loading.value = true;
  try {
    const res = await listExports(page.value, size.value, statusFilter.value);
    rows.value = res.records ?? [];
    total.value = res.total ?? 0;
  } catch (e) {
    message.error(e instanceof Error ? e.message : '导出记录加载失败');
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function onPageChange(p: number, s: number) {
  page.value = p;
  size.value = s;
  load();
}

async function handleExport() {
  if (!selectedDefId.value) return message.warning('请选择要导出的报表定义');
  submitting.value = true;
  try {
    await createExport(selectedDefId.value);
    message.success('导出任务已提交，稍后在列表中查看结果');
    page.value = 1;
    await load();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '发起导出失败');
  } finally {
    submitting.value = false;
  }
}

function fmt(t?: string): string {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-';
}

function defName(id: number): string {
  return defs.value.find((d) => d.id === id)?.name || `#${id}`;
}

onMounted(async () => {
  await loadDefs();
  await load();
});
</script>

<template>
  <div class="export">
    <section class="head">
      <h2 class="head-title">导出记录</h2>
      <p class="head-sub">
        报表定义的异步导出任务；导出属数据外流操作，需单独的导出权限。
      </p>
    </section>

    <a-space class="toolbar" wrap>
      <template v-if="canExport">
        <span>报表定义</span>
        <a-select
          v-model:value="selectedDefId"
          :options="defs.map((d) => ({ value: d.id, label: d.name }))"
          placeholder="选择报表定义"
          style="width: 260px"
        />
        <a-button type="primary" :loading="submitting" @click="handleExport">发起导出</a-button>
      </template>
      <a-alert v-else type="info" show-icon message="当前账号无导出权限（plt:report:export）" />

      <span>状态</span>
      <a-select
        v-model:value="statusFilter"
        :options="[
          { value: 'PENDING', label: '排队中' },
          { value: 'DONE', label: '已完成' },
          { value: 'FAILED', label: '失败' },
        ]"
        allow-clear
        placeholder="全部"
        style="width: 140px"
        @change="() => { page = 1; load(); }"
      />
    </a-space>

    <a-table
      :data-source="rows"
      :loading="loading"
      row-key="id"
      size="middle"
      :pagination="{
        current: page,
        pageSize: size,
        total,
        showSizeChanger: true,
        showTotal: (n: number) => `共 ${n} 条`,
        onChange: onPageChange,
      }"
      :locale="{ emptyText: '暂无导出任务' }"
    >
      <a-table-column key="id" title="任务 ID" data-index="id" :width="180" />
      <a-table-column key="reportDefId" title="报表" :width="200">
        <template #default="{ record }">{{ defName(record.reportDefId) }}</template>
      </a-table-column>
      <a-table-column key="status" title="状态" :width="120">
        <template #default="{ record }">
          <a-tag :color="record.status === 'DONE' ? 'green' : record.status === 'FAILED' ? 'red' : 'blue'">
            {{ STATUS_MAP[record.status] || record.status }}
          </a-tag>
        </template>
      </a-table-column>
      <a-table-column key="fileId" title="文件 ID" :width="180">
        <template #default="{ record }">{{ record.fileId ?? '-' }}</template>
      </a-table-column>
      <a-table-column key="createdAt" title="创建时间" :width="180">
        <template #default="{ record }">{{ fmt(record.createdAt) }}</template>
      </a-table-column>
    </a-table>
  </div>
</template>

<style scoped>
.export {
  padding: 4px;
}
.head {
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
.toolbar {
  margin-bottom: 16px;
}
</style>
