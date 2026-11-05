<script setup lang="ts">
/**
 * 算薪批次操作区（批 9）：环比对比 / 双人复核 / 报盘导出。
 * 复核仅 REVIEW 状态可用；报盘使用独立权限码 cmp:payroll-run:export（后端 RequirePerm）。
 */
import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import { DownloadOutlined } from '@ant-design/icons-vue';
import PayrollCompareModal from './PayrollCompareModal.vue';
import PayrollReviewModal from './PayrollReviewModal.vue';
import { downloadPayrollBankExport } from '../../../api/compensation';
import { errorText } from '../crud-helpers';
import { useUserStore } from '../../../stores/user';
import type { PayrollRun } from '../../../types/admin';

const props = defineProps<{ run: PayrollRun | null }>();
const emit = defineEmits<{ done: [] }>();

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('cmp:payroll-run:write'));
const canExport = computed(() => userStore.hasPerm('cmp:payroll-run:export'));

const compareOpen = ref(false);
const reviewOpen = ref(false);
const exporting = ref(false);

const reviewable = computed(() => props.run?.status === 'REVIEW');

function openCompare() {
  if (!props.run) return message.warning('请先选择核算批次');
  compareOpen.value = true;
}

function openReview() {
  if (!props.run) return message.warning('请先选择核算批次');
  if (!reviewable.value) return message.warning('仅 REVIEW 状态批次可复核');
  reviewOpen.value = true;
}

/** 报盘下载：Blob → 临时对象 URL → 触发浏览器下载 */
async function handleExport() {
  if (!props.run) return message.warning('请先选择核算批次');
  exporting.value = true;
  try {
    const blob = await downloadPayrollBankExport(props.run.id);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `payroll_${props.run.periodCode}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    message.success('报盘文件已开始下载');
  } catch (e) {
    message.error(errorText(e, '报盘导出失败'));
  } finally {
    exporting.value = false;
  }
}
</script>

<template>
  <a-space>
    <a-button :disabled="!run" @click="openCompare">环比对比</a-button>
    <a-button v-if="canWrite && reviewable" :disabled="!run" @click="openReview">双人复核</a-button>
    <a-button v-if="canExport" :disabled="!run" :loading="exporting" @click="handleExport">
      <template #icon><DownloadOutlined /></template>
      报盘导出
    </a-button>
    <PayrollCompareModal v-model:open="compareOpen" :run-id="run?.id ?? null" />
    <PayrollReviewModal v-model:open="reviewOpen" :run="run" @done="emit('done')" />
  </a-space>
</template>
