<script setup lang="ts">
/**
 * 双人复核弹窗（CMP-F-013b）：REVIEW 批次由两名不同用户各复核一次，
 * 第二位通过后自动流转 APPROVE；同人重复复核被拒，驳回退回 CALC。
 */
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { reviewPayrollRun } from '../../../api/compensation';
import { errorText } from '../crud-helpers';
import type { PayrollRun } from '../../../types/admin';

const props = defineProps<{ open: boolean; run: PayrollRun | null }>();
const emit = defineEmits<{ 'update:open': [boolean]; done: [] }>();

const approved = ref(true);
const comment = ref('');
const submitting = ref(false);

const canReview = computed(() => props.run?.status === 'REVIEW');

function fmt(v?: string | null): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '未复核';
}

async function handleOk() {
  const run = props.run;
  if (!run) return;
  submitting.value = true;
  try {
    const updated = await reviewPayrollRun(run.id, approved.value, comment.value.trim() || undefined);
    message.success(
      approved.value
        ? `复核通过，批次当前状态：${updated.status}`
        : '已驳回，批次退回计算（CALC）',
    );
    emit('update:open', false);
    emit('done');
  } catch (e) {
    message.error(errorText(e, '复核提交失败'));
  } finally {
    submitting.value = false;
  }
}

watch(
  () => props.open,
  (v) => {
    if (v) {
      approved.value = true;
      comment.value = '';
    }
  },
);
</script>

<template>
  <a-modal
    :open="open"
    title="双人复核"
    :confirm-loading="submitting"
    :ok-button-props="{ disabled: !canReview }"
    ok-text="提交复核"
    cancel-text="取消"
    @update:open="emit('update:open', $event)"
    @ok="handleOk"
  >
    <a-descriptions v-if="run" :column="1" size="small" bordered class="brief">
      <a-descriptions-item label="批次">#{{ run.id }} / {{ run.periodCode }}</a-descriptions-item>
      <a-descriptions-item label="当前状态">{{ run.status }}</a-descriptions-item>
      <a-descriptions-item label="复核人 1">
        {{ run.reviewedBy1 ?? '—' }}（{{ fmt(run.reviewedAt1) }}）
      </a-descriptions-item>
      <a-descriptions-item label="复核人 2">
        {{ run.reviewedBy2 ?? '—' }}（{{ fmt(run.reviewedAt2) }}）
      </a-descriptions-item>
      <a-descriptions-item v-if="run.reviewComment" label="最近意见">
        {{ run.reviewComment }}
      </a-descriptions-item>
    </a-descriptions>

    <a-alert
      v-if="!canReview"
      type="warning"
      show-icon
      class="tip"
      message="仅 REVIEW 状态批次可复核"
    />

    <a-form layout="vertical" class="form">
      <a-form-item label="复核结论" required>
        <a-radio-group v-model:value="approved" :disabled="!canReview">
          <a-radio :value="true">通过</a-radio>
          <a-radio :value="false">驳回（退回计算）</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="复核意见">
        <a-textarea v-model:value="comment" :rows="3" :disabled="!canReview" placeholder="驳回建议填写原因" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped>
.brief {
  margin-bottom: 12px;
}
.tip {
  margin-bottom: 12px;
}
.form {
  margin-top: 8px;
}
</style>
