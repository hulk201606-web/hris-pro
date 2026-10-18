<script setup lang="ts">
import { reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { SearchOutlined } from '@ant-design/icons-vue';
import { BizError } from '../../api/error';
import EmptyHint from './EmptyHint.vue';
import type { EmployeeProfileBrief } from '../../types/admin';

/**
 * 员工档案简报取数面板（跨域内部 Feign）。
 * 绩效评估上下文与知识可见范围共用，通过 fetcher 注入各域取数接口。
 */
const props = defineProps<{
  title: string;
  subtitle: string;
  fetcher: (empId: number) => Promise<EmployeeProfileBrief>;
}>();

// 员工 ID 需按实际租户数据填写，故默认留空并由校验拦截，不预置演示 ID。
const formState = reactive({ empIdText: '' });
const loading = ref(false);
const queried = ref(false);
const context = ref<EmployeeProfileBrief | null>(null);

const statusLabels: Record<string, string> = {
  '0': '待入职',
  '1': '试用',
  '2': '在职',
  '3': '离职中',
  '4': '已离职',
  '5': '已取消',
  '6': '退休',
  '7': '黑名单',
};

function statusText(v: number | string | undefined): string {
  if (v === undefined || v === null || v === '') return '—';
  return statusLabels[String(v)] ?? String(v);
}

function dash(v: unknown): string {
  return v === undefined || v === null || v === '' ? '—' : String(v);
}

async function handleQuery() {
  const empId = Number(formState.empIdText.trim());
  if (!Number.isInteger(empId) || empId <= 0) {
    message.warning('请输入有效的员工 ID');
    return;
  }
  loading.value = true;
  try {
    context.value = await props.fetcher(empId);
    queried.value = true;
    if (!context.value) message.info('未获取到该员工的档案简报');
  } catch (e) {
    message.error(e instanceof BizError ? e.message : '档案简报取数失败');
    context.value = null;
    queried.value = true;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="context-page">
    <section class="head">
      <h2 class="head-title">{{ title }}</h2>
      <p class="head-sub">{{ subtitle }}</p>
    </section>

    <a-card :bordered="false" class="form-card">
      <a-form layout="inline" :model="formState">
        <a-form-item label="员工 ID">
          <a-input
            v-model:value="formState.empIdText"
            placeholder="如 200001"
            style="width: 220px"
            @press-enter="handleQuery"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="loading" @click="handleQuery">
            <template #icon><SearchOutlined /></template>
            查询上下文
          </a-button>
        </a-form-item>
      </a-form>
      <p class="form-tip">员工 ID 取自员工档案，可在「我的信息」或员工列表中查看。</p>
    </a-card>

    <a-card :bordered="false" class="result-card">
      <a-descriptions v-if="context" bordered :column="2" size="middle">
        <a-descriptions-item label="员工 ID">{{ dash(context.empId) }}</a-descriptions-item>
        <a-descriptions-item label="姓名">{{ dash(context.name) }}</a-descriptions-item>
        <a-descriptions-item label="工号">{{ dash(context.empNo) }}</a-descriptions-item>
        <a-descriptions-item label="在职状态">{{ statusText(context.status) }}</a-descriptions-item>
        <a-descriptions-item label="租户 ID">{{ dash(context.tenantId) }}</a-descriptions-item>
        <a-descriptions-item label="部门 ID">{{ dash(context.deptId) }}</a-descriptions-item>
        <a-descriptions-item label="岗位 ID">{{ dash(context.positionId) }}</a-descriptions-item>
        <a-descriptions-item label="汇报对象 ID">{{ dash(context.supervisorEmpId) }}</a-descriptions-item>
      </a-descriptions>
      <a-skeleton v-else-if="loading" active :paragraph="{ rows: 4 }" />
      <EmptyHint
        v-else
        :description="queried ? '未查询到档案简报' : '尚未查询'"
        :hint="queried ? '请确认员工 ID 是否存在于当前租户' : '输入员工 ID 后点击「查询上下文」'"
      />
    </a-card>
  </div>
</template>

<style scoped>
.context-page {
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
.result-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.form-tip {
  margin: var(--space-3) 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
