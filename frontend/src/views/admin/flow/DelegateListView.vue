<script setup lang="ts">
/**
 * 管理端-委托代理：生效期内被委托人可代为处理我的待办任务。
 * 提供我的委托列表、新建委托弹窗与提前结束；按契约以 flow:task:write 控显。
 * 后端委派模块并行开发中，字段取数已做可选兜底。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { createDelegate, endDelegate, listMyDelegates } from '../../../api/flow';
import type { FlowDelegate } from '../../../api/flow';
import { useUserStore } from '../../../stores/user';

const userStore = useUserStore();
const canManage = computed(() => userStore.hasPerm('flow:task:write'));

const rows = ref<FlowDelegate[]>([]);
const loading = ref(false);

const tableColumns = [
  { title: '被委托人 ID', key: 'toUser', width: 120 },
  { title: '流程定义', key: 'procDefKey', width: 160 },
  { title: '开始时间', key: 'startAt', width: 160 },
  { title: '结束时间', key: 'endAt', width: 160 },
  { title: '事由', key: 'reason' },
  { title: '状态', key: 'status', width: 110 },
  { title: '操作', key: 'action', width: 120, fixed: 'right' as const },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await listMyDelegates();
  } catch (e) {
    message.error(errorText(e, '委托列表加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function timeText(value?: string): string {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—';
}

function statusColor(status?: string): string {
  if (status === 'ACTIVE') return 'green';
  if (status === 'ENDED' || status === 'EXPIRED') return 'default';
  return 'blue';
}

/* ------------------------------ 新建委托 ------------------------------ */

const createOpen = ref(false);
const submitting = ref(false);
const form = reactive({
  toUser: undefined as number | undefined,
  procDefKey: '',
  range: [] as string[],
  reason: '',
});

function openCreate() {
  form.toUser = undefined;
  form.procDefKey = '';
  form.range = [];
  form.reason = '';
  createOpen.value = true;
}

async function handleCreate() {
  if (!form.toUser) {
    message.warning('请填写被委托人 ID');
    return;
  }
  if (!form.range[0] || !form.range[1]) {
    message.warning('请选择委托时间段');
    return;
  }
  submitting.value = true;
  try {
    await createDelegate({
      toUser: form.toUser,
      ...(form.procDefKey.trim() ? { procDefKey: form.procDefKey.trim() } : {}),
      startAt: form.range[0],
      endAt: form.range[1],
      ...(form.reason.trim() ? { reason: form.reason.trim() } : {}),
    });
    message.success('委托已创建');
    createOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, '委托创建失败'));
  } finally {
    submitting.value = false;
  }
}

async function handleEnd(record: FlowDelegate) {
  try {
    await endDelegate(record.id);
    message.success('委托已提前结束');
    await load();
  } catch (e) {
    message.error(errorText(e, '结束委托失败'));
  }
}

onMounted(load);
</script>

<template>
  <div class="delegate-page">
    <section class="head">
      <div>
        <h2 class="head-title">委托代理</h2>
        <p class="head-sub">请假或休假期间将待办委托给同事代为处理，可随时提前结束</p>
      </div>
      <a-space>
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canManage" type="primary" @click="openCreate">新建委托</a-button>
      </a-space>
    </section>

    <a-card :bordered="false" class="panel">
      <a-table
        row-key="id"
        :columns="tableColumns"
        :data-source="rows"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        :scroll="{ x: 880 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'procDefKey'">
            {{ record.procDefKey || '全部流程' }}
          </template>
          <template v-else-if="column.key === 'startAt' || column.key === 'endAt'">
            {{ timeText(record[column.key]) }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ record.status || '生效中' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-popconfirm
              v-if="canManage"
              title="确认提前结束该委托？"
              @confirm="handleEnd(record)"
            >
              <a-button type="link" size="small" danger>提前结束</a-button>
            </a-popconfirm>
            <span v-else>—</span>
          </template>
          <template v-else>{{ record[column.key] ?? '—' }}</template>
        </template>
        <template #emptyText>
          <EmptyHint
            description="暂无委托记录"
            :hint="canManage ? '点击右上角「新建委托」创建第一条委托' : ''"
          />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="createOpen"
      title="新建委托"
      :confirm-loading="submitting"
      :mask-closable="false"
      ok-text="创建"
      cancel-text="取消"
      @ok="handleCreate"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="被委托人 ID" required>
          <a-input-number v-model:value="form.toUser" :min="1" placeholder="同事的用户 ID" style="width: 100%" />
        </a-form-item>
        <a-form-item label="流程定义 Key">
          <a-input v-model:value="form.procDefKey" placeholder="选填，如 LEAVE_APPLY；留空表示全部流程" />
        </a-form-item>
        <a-form-item label="委托时间段" required>
          <a-range-picker
            v-model:value="form.range"
            show-time
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="事由">
          <a-textarea v-model:value="form.reason" :rows="3" placeholder="选填，如 年假休假期间委托处理" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.delegate-page {
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
