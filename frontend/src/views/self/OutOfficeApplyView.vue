<script setup lang="ts">
/**
 * 自助端-外出/居家办公申请（ATT-F-018）：发起表单 + 我的申请列表。
 * empId 由后端取当前登录用户，前端不传；后端无撤回端点，故不提供撤回操作。
 */
import { onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../components/admin/EmptyHint.vue';
import { errorText } from '../../components/admin/crud-helpers';
import { createOutOffice, fetchMyOutOffices } from '../../api/out-office';
import type { OutOfficeItem } from '../../api/out-office';

const TYPE_OPTIONS = [
  { label: '外出', value: 'OUT' },
  { label: '居家办公', value: 'WFH' },
];

const form = reactive({
  type: 'OUT' as 'OUT' | 'WFH',
  startTime: '',
  endTime: '',
  reason: '',
});
const submitting = ref(false);

const rows = ref<OutOfficeItem[]>([]);
const loading = ref(false);

const listColumns = [
  { title: '类型', key: 'type', width: 100 },
  { title: '开始时间', key: 'startTime', width: 170 },
  { title: '结束时间', key: 'endTime', width: 170 },
  { title: '事由', key: 'reason' },
  { title: '状态', key: 'status', width: 110 },
];

function typeText(value: string): string {
  return TYPE_OPTIONS.find((o) => o.value === value)?.label ?? value;
}

function statusColor(status: string): string {
  if (status === 'APPROVED') return 'green';
  if (status === 'REJECTED') return 'red';
  return 'blue';
}

async function load() {
  loading.value = true;
  try {
    rows.value = await fetchMyOutOffices();
  } catch (e) {
    message.error(errorText(e, '申请记录加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  if (!form.startTime || !form.endTime) {
    message.warning('请选择起止时间');
    return;
  }
  if (form.endTime <= form.startTime) {
    message.warning('结束时间必须晚于开始时间');
    return;
  }
  submitting.value = true;
  try {
    await createOutOffice({
      type: form.type,
      startTime: form.startTime,
      endTime: form.endTime,
      ...(form.reason.trim() ? { reason: form.reason.trim() } : {}),
    });
    message.success('申请已提交，等待审批');
    form.reason = '';
    await load();
  } catch (e) {
    message.error(errorText(e, '提交失败'));
  } finally {
    submitting.value = false;
  }
}

function timeText(value: string): string {
  return String(value || '').replace('T', ' ').slice(0, 16);
}

onMounted(load);
</script>

<template>
  <div class="out-office-page">
    <section class="head">
      <div>
        <h2 class="head-title">外出 / 居家办公申请</h2>
        <p class="head-sub">提交后自动进入审批，审批通过前请保持可联系状态</p>
      </div>
      <a-button @click="load">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </section>

    <a-card :bordered="false" class="form-card">
      <a-form layout="vertical" :model="form">
        <a-form-item label="类型" required>
          <a-radio-group v-model:value="form.type" :options="TYPE_OPTIONS" option-type="button" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="开始时间" required>
              <a-date-picker
                v-model:value="form.startTime"
                show-time
                value-format="YYYY-MM-DDTHH:mm:ss"
                placeholder="请选择开始时间"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="结束时间" required>
              <a-date-picker
                v-model:value="form.endTime"
                show-time
                value-format="YYYY-MM-DDTHH:mm:ss"
                placeholder="请选择结束时间"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="事由">
          <a-textarea v-model:value="form.reason" :rows="3" placeholder="选填，说明外出地点或居家工作安排" />
        </a-form-item>
        <a-button type="primary" :loading="submitting" block @click="handleSubmit">提交申请</a-button>
      </a-form>
    </a-card>

    <a-card :bordered="false" class="list-card" title="我的申请">
      <a-table
        row-key="id"
        :columns="listColumns"
        :data-source="rows"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <a-tag :color="record.type === 'OUT' ? 'orange' : 'cyan'">{{ typeText(record.type) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'startTime' || column.key === 'endTime'">
            {{ timeText(record[column.key]) }}
          </template>
          <template v-else>{{ record[column.key] || '—' }}</template>
        </template>
        <template #emptyText>
          <EmptyHint description="暂无外出/居家申请" hint="通过上方表单发起第一条申请" />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.out-office-page {
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

.form-card,
.list-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
</style>
