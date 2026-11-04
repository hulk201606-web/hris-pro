<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import WorkdayCheckPanel from '../../../components/admin/attendance/WorkdayCheckPanel.vue';
import { errorText, formatCell } from '../../../components/admin/crud-helpers';
import { useUserStore } from '../../../stores/user';
import {
  createHoliday,
  deleteHoliday,
  listHolidays,
  updateHoliday,
} from '../../../api/attendance-holiday';
import type { HolidayItem } from '../../../api/attendance-holiday';
import type { CrudColumn } from '../../../types/admin';

/**
 * 管理端-节假日历（ATT-F-021）。
 * 与普通配置页的差别：这里既要维护日历，也要让管理员能直接验证
 * 「某天到底算不算工作日」——调休制度下周末可能上班、周一可能放假，
 * 只靠星期几推断会算错，所以判定结果必须可见、可核对。
 */
const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('att:holiday:write'));

const DAY_TYPE_MAP: Record<string, string> = {
  HOLIDAY: '法定节假日（放假）',
  WORKDAY: '调班补班（上班）',
};
const columns: CrudColumn[] = [
  { key: 'holidayDate', title: '日期', width: 140, type: 'date' },
  { key: 'name', title: '名称', width: 180 },
  { key: 'dayType', title: '日期类型', width: 180, type: 'tag', valueMap: DAY_TYPE_MAP },
  { key: 'remark', title: '备注' },
];

const tableColumns = computed<any[]>(() => [
  ...columns,
  { title: '操作', key: 'action', width: 160, fixed: 'right' },
]);

function cellText(key: string, value: unknown): string {
  return formatCell(columns.find((c) => c.key === key), value);
}

function typeColor(value: unknown): string {
  return value === 'HOLIDAY' ? 'red' : 'blue';
}

const thisYear = new Date().getFullYear();
const year = ref<number>(thisYear);
const yearOptions = Array.from({ length: 5 }, (_, i) => ({
  label: `${thisYear - 2 + i} 年`,
  value: thisYear - 2 + i,
}));

const rows = ref<HolidayItem[]>([]);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    rows.value = await listHolidays(year.value);
  } catch (e) {
    message.error(errorText(e, '日历加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

watch(year, load);
onMounted(load);

/* ------------------------------ 新建 / 编辑 ------------------------------ */

const modalOpen = ref(false);
const editingId = ref<number | null>(null);
const submitting = ref(false);
const formState = reactive<Record<string, any>>({});

function resetForm() {
  Object.keys(formState).forEach((k) => delete formState[k]);
  formState.dayType = 'HOLIDAY';
}

function openCreate() {
  resetForm();
  editingId.value = null;
  modalOpen.value = true;
}

function openEdit(record: HolidayItem) {
  resetForm();
  formState.holidayDate = record.holidayDate;
  formState.name = record.name;
  formState.dayType = record.dayType;
  formState.remark = record.remark ?? '';
  editingId.value = record.id;
  modalOpen.value = true;
}

async function submit() {
  if (!formState.holidayDate || !formState.name || !formState.dayType) {
    message.warning('请填写「日期」「名称」「日期类型」');
    return;
  }
  const payload: Record<string, unknown> = {
    holidayDate: formState.holidayDate,
    name: formState.name,
    dayType: formState.dayType,
  };
  if (formState.remark) payload.remark = formState.remark;
  submitting.value = true;
  try {
    if (editingId.value === null) {
      await createHoliday(payload);
      message.success('创建成功');
    } else {
      await updateHoliday(editingId.value, payload);
      message.success('保存成功');
    }
    modalOpen.value = false;
    await load();
  } catch (e) {
    message.error(errorText(e, '保存失败'));
  } finally {
    submitting.value = false;
  }
}

async function removeRow(record: HolidayItem) {
  try {
    await deleteHoliday(record.id);
    message.success('已删除');
    await load();
  } catch (e) {
    message.error(errorText(e, '删除失败'));
  }
}

</script>

<template>
  <div class="holiday-page">
    <section class="head">
      <div>
        <h2 class="head-title">节假日历</h2>
        <p class="head-sub">维护法定节假日与调班补班日，为日考勤计算提供工作日判定依据</p>
      </div>
      <a-space>
        <a-select v-model:value="year" :options="yearOptions" style="width: 110px" />
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建</a-button>
      </a-space>
    </section>

    <WorkdayCheckPanel />

    <a-card :bordered="false" class="panel">
      <a-table
        row-key="id"
        :columns="tableColumns"
        :data-source="rows"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'dayType'">
            <a-tag :color="typeColor(record.dayType)">{{ cellText('dayType', record.dayType) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space v-if="canWrite">
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确认删除这条日历记录？" @confirm="removeRow(record)">
                <a-button type="link" danger size="small">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
          <template v-else>
            {{ cellText(column.key, record[column.key]) }}
          </template>
        </template>
        <template #emptyText>
          <EmptyHint
            :description="`${year} 年暂无日历记录`"
            :hint="canWrite ? '点击右上角「新建」添加节假日或调班日' : ''"
          />
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId === null ? '新建日历记录' : '编辑日历记录'"
      :confirm-loading="submitting"
      :mask-closable="false"
      ok-text="保存"
      cancel-text="取消"
      @ok="submit"
    >
      <a-form :model="formState" layout="vertical">
        <a-form-item label="日期" required>
          <a-date-picker v-model:value="formState.holidayDate" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
        <a-form-item label="名称" required>
          <a-input v-model:value="formState.name" placeholder="如 春节 / 国庆调休补班" />
        </a-form-item>
        <a-form-item label="日期类型" required>
          <a-select
            v-model:value="formState.dayType"
            :options="[
              { label: '法定节假日（放假）', value: 'HOLIDAY' },
              { label: '调班补班（上班）', value: 'WORKDAY' },
            ]"
          />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="formState.remark" :rows="2" placeholder="如 依据国务院办公厅放假通知" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.holiday-page {
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
