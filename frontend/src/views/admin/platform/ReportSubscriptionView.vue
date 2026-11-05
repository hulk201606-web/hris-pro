<script setup lang="ts">

/**
 * 报表订阅（PLT-F-005）：列表（报表定义 / CRON / 渠道 / 收件人 / 状态）+ 新建编辑 + 启停。
 * 后端 ReportSubscriptionController：读 plt:report:read，写 plt:report:write。
 * 启用即注册调度任务（task_key=RPT_SUB_<id>，handler=REPORT_SUBSCRIPTION_DISPATCH），
 * 停用只下线任务不删记录；scheduled 表示运行时是否已装载。
 */
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import ReportSubscriptionModal from '../../../components/admin/platform/ReportSubscriptionModal.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { listReportDefs } from '../../../api/platform';
import {
  deleteReportSubscription,
  listReportSubscriptions,
  updateReportSubscription,
} from '../../../api/report-subscription';
import { useUserStore } from '../../../stores/user';
import type { ReportDefResponse } from '../../../types/api';
import type { ReportSubscription } from '../../../types/api';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('plt:report:write'));

const CHANNEL_LABELS: Record<string, string> = {
  EMAIL: '邮件',
  WECOM: '企业微信',
  DINGTALK: '钉钉',
};

const rows = ref<ReportSubscription[]>([]);
const defs = ref<ReportDefResponse[]>([]);
const loading = ref(false);

const modalOpen = ref(false);
const editing = ref<ReportSubscription | null>(null);

const defName = computed<Record<number, string>>(() => {
  const map: Record<number, string> = {};
  defs.value.forEach((d) => {
    map[d.id] = d.name;
  });
  return map;
});
const defOptions = computed(() =>
  defs.value.map((d) => ({ label: `${d.name}（${d.code}）`, value: d.id })),
);

function channelText(channels: string[]): string {
  if (!channels || channels.length === 0) return '—';
  return channels.map((c) => CHANNEL_LABELS[c] ?? c).join(' / ');
}

async function load() {
  loading.value = true;
  try {
    rows.value = await listReportSubscriptions();
  } catch (e) {
    message.error(errorText(e, '订阅列表加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadDefs() {
  try {
    defs.value = await listReportDefs();
  } catch {
    defs.value = [];
  }
}

function openCreate() {
  editing.value = null;
  modalOpen.value = true;
}

function openEdit(record: ReportSubscription) {
  editing.value = record;
  modalOpen.value = true;
}

/** 启停：复用更新端点，启用即注册调度、停用即下线 */
async function toggle(record: ReportSubscription) {
  const next = record.status === 1 ? 0 : 1;
  try {
    await updateReportSubscription(record.id, {
      reportDefId: record.reportDefId,
      cronExpr: record.cronExpr,
      channels: record.channels ?? [],
      recipients: record.recipients ?? [],
      status: next,
    });
    message.success(next === 1 ? '已启用，调度任务已注册' : '已停用，调度任务已下线');
    await load();
  } catch (e) {
    message.error(errorText(e, '状态切换失败'));
  }
}

async function remove(record: ReportSubscription) {
  try {
    await deleteReportSubscription(record.id);
    message.success('已删除并下线调度任务');
    await load();
  } catch (e) {
    message.error(errorText(e, '删除失败'));
  }
}

onMounted(() => {
  load();
  loadDefs();
});
</script>

<template>
  <div class="sub-page">
    <section class="head">
      <div>
        <h2 class="head-title">报表订阅</h2>
        <p class="head-sub">按 CRON 周期推送报表，启用即注册调度任务；停用仅下线任务不删订阅</p>
      </div>
      <a-space>
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建订阅</a-button>
      </a-space>
    </section>

    <a-card :bordered="false" class="card">
      <a-table
        row-key="id"
        :columns="[
          { title: '报表定义', key: 'reportDefId', width: 200 },
          { title: 'CRON', dataIndex: 'cronExpr', key: 'cronExpr', width: 160 },
          { title: '渠道', key: 'channels', width: 180 },
          { title: '收件人', key: 'recipients', width: 220 },
          { title: '状态', key: 'status', width: 110 },
          { title: '调度', key: 'scheduled', width: 110 },
          { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
          { title: '操作', key: 'action', width: 180, fixed: 'right' },
        ]"
        :data-source="rows"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        :scroll="{ x: 1200 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'reportDefId'">
            {{ defName[record.reportDefId] ?? `报表 #${record.reportDefId}` }}
          </template>
          <template v-else-if="column.key === 'channels'">{{ channelText(record.channels) }}</template>
          <template v-else-if="column.key === 'recipients'">
            {{ (record.recipients ?? []).join('、') || '—' }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'green' : 'default'">
              {{ record.status === 1 ? '启用' : '停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'scheduled'">
            <a-tag :color="record.scheduled ? 'blue' : 'orange'">
              {{ record.scheduled ? '已装载' : '未装载' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ (record.updatedAt || '').replace('T', ' ').slice(0, 19) || '—' }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="canWrite">
              <a-popconfirm
                :title="record.status === 1 ? '停用后不再按 CRON 推送，确认？' : '启用后将注册调度任务，确认？'"
                @confirm="toggle(record)"
              >
                <a-button type="link" size="small">{{ record.status === 1 ? '停用' : '启用' }}</a-button>
              </a-popconfirm>
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="删除订阅并下线调度任务，确认？" @confirm="remove(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
            <span v-else class="muted">—</span>
          </template>
        </template>
        <template #emptyText>
          <EmptyHint description="暂无报表订阅" :hint="canWrite ? '点击右上角「新建订阅」配置第一个周期推送' : ''" />
        </template>
      </a-table>
    </a-card>

    <ReportSubscriptionModal
      v-model:open="modalOpen"
      :record="editing"
      :def-options="defOptions"
      @saved="load"
    />
  </div>
</template>

<style scoped>
.sub-page {
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

.card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.muted {
  color: var(--color-text-muted);
}
</style>
