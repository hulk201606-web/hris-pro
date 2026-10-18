<script setup lang="ts">
import { reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { SearchOutlined } from '@ant-design/icons-vue';
import { previewPayroll } from '../../../api/compensation';
import { BizError } from '../../../api/error';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import type { PayrollPreviewLine } from '../../../types/admin';

/** 当前自然月（YYYY-MM），作为算薪周期的默认值，避免写死月份。 */
function currentPeriod(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

// 员工 ID 需按实际租户数据填写，故默认留空并由校验拦截，不预置演示 ID。
const formState = reactive({ periodCode: currentPeriod(), empIdsText: '' });
const loading = ref(false);
const queried = ref(false);
const lines = ref<PayrollPreviewLine[]>([]);

const columns = [
  { title: '员工 ID', dataIndex: 'empId', key: 'empId', width: 110 },
  { title: '工号', dataIndex: 'empNo', key: 'empNo', width: 130 },
  { title: '姓名', dataIndex: 'name', key: 'name', width: 120 },
  { title: '部门 ID', dataIndex: 'deptId', key: 'deptId', width: 110 },
  { title: '考勤周期状态', dataIndex: 'attPeriodStatus', key: 'attPeriodStatus', width: 140 },
  { title: '加班时长(小时)', dataIndex: 'overtimeHours', key: 'overtimeHours', width: 130 },
  { title: '请假时长(小时)', dataIndex: 'leaveHours', key: 'leaveHours', width: 130 },
  { title: '当期人数', dataIndex: 'periodEmployeeCount', key: 'periodEmployeeCount', width: 110 },
];

function parseEmpIds(text: string): number[] {
  return text
    .split(/[,，\s]+/)
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isInteger(n) && n > 0);
}

async function handlePreview() {
  const periodCode = formState.periodCode.trim();
  if (!periodCode) {
    message.warning('请填写薪酬周期');
    return;
  }
  const empIds = parseEmpIds(formState.empIdsText);
  if (!empIds.length) {
    message.warning('请填写员工 ID（至少一个）');
    return;
  }
  loading.value = true;
  try {
    lines.value = await previewPayroll(periodCode, empIds);
    queried.value = true;
    if (!lines.value.length) message.info('未获取到预览数据，请确认周期与员工是否存在');
  } catch (e) {
    message.error(e instanceof BizError ? e.message : '算薪取数失败');
    lines.value = [];
    queried.value = true;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="preview-page">
    <section class="head">
      <h2 class="head-title">算薪取数预览</h2>
      <p class="head-sub">
        跨域组合员工主档与考勤月结/日明细，核算前核对加班、请假与出勤汇总，不做落库。
      </p>
    </section>

    <a-card :bordered="false" class="form-card">
      <a-form layout="inline" :model="formState" class="query-form">
        <a-form-item label="薪酬周期">
          <a-input v-model:value="formState.periodCode" placeholder="YYYY-MM，如 2026-10" style="width: 160px" />
        </a-form-item>
        <a-form-item label="员工 ID">
          <a-input
            v-model:value="formState.empIdsText"
            placeholder="多个用逗号分隔，如 200001,200002"
            style="width: 280px"
            @press-enter="handlePreview"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="loading" @click="handlePreview">
            <template #icon><SearchOutlined /></template>
            生成预览
          </a-button>
        </a-form-item>
      </a-form>
      <p class="form-tip">员工 ID 取自员工档案，可在「我的信息」或员工列表中查看。</p>
    </a-card>

    <a-card :bordered="false" class="table-card">
      <a-table
        row-key="empId"
        :columns="columns"
        :data-source="lines"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1000 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'attPeriodStatus'">
            <a-tag :color="record.attPeriodStatus === 'CLOSED' ? 'green' : 'blue'">
              {{ record.attPeriodStatus || '—' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'overtimeHours' || column.key === 'leaveHours'">
            {{ record[column.key] === null || record[column.key] === undefined ? '—' : Number(record[column.key]).toFixed(1) }}
          </template>
          <template v-else-if="column.key === 'deptId'">
            {{ record.deptId === null || record.deptId === undefined ? '—' : record.deptId }}
          </template>
        </template>
        <template #emptyText>
          <EmptyHint
            :description="queried ? '该周期无匹配员工数据' : '尚未生成预览'"
            :hint="queried ? '请核对薪酬周期与员工 ID 是否存在于当前租户' : '填写薪酬周期与员工 ID 后点击「生成预览」'"
          />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.preview-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
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
.table-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.form-tip {
  margin: var(--space-3) 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

.query-form {
  row-gap: var(--space-3);
}
</style>
