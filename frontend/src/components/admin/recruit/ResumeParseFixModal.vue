<script setup lang="ts">
/**
 * 简历解析人工修正弹窗：可编辑 parsed 三字段 + 教育/工作经历 JSON 文本，
 * 走 PUT /rcu/resumes/{id}/parse（未传字段不更新；parse_status 服务端保持不变）。
 */
import { reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { updateResumeParse } from '../../../api/recruit';
import { errorText } from '../crud-helpers';
import type { ResumeParseResult } from '../../../types/api-batch2';

const props = defineProps<{
  open: boolean;
  resumeId: number;
  /** 当前解析结果（打开时回填表单） */
  result: ResumeParseResult | null;
}>();

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'saved', result: ResumeParseResult): void;
}>();

const saving = ref(false);
const form = reactive({
  parsedName: '',
  parsedMobile: '',
  parsedEmail: '',
  educationText: '',
  experienceText: '',
});

function pretty(v: unknown): string {
  if (!v) return '';
  if (Array.isArray(v)) return JSON.stringify(v, null, 2);
  try {
    return JSON.stringify(JSON.parse(String(v)), null, 2);
  } catch {
    return String(v);
  }
}

watch(
  () => props.open,
  (v) => {
    if (!v || !props.result) return;
    form.parsedName = props.result.parsedName ?? '';
    form.parsedMobile = props.result.parsedMobile ?? '';
    form.parsedEmail = props.result.parsedEmail ?? '';
    form.educationText = pretty(props.result.educationJson);
    form.experienceText = pretty(props.result.experienceJson);
  },
);

async function handleSubmit() {
  if (!form.parsedName.trim()) {
    message.warning('请填写姓名');
    return;
  }
  const payload: Record<string, unknown> = {
    parsedName: form.parsedName.trim(),
    parsedMobile: form.parsedMobile,
    parsedEmail: form.parsedEmail,
  };
  for (const [key, text, label] of [
    ['educationJson', form.educationText, '教育经历'],
    ['experienceJson', form.experienceText, '工作经历'],
  ] as const) {
    if (text.trim()) {
      try {
        payload[key] = JSON.parse(text);
      } catch {
        message.warning(`${label}须为合法 JSON 数组`);
        return;
      }
    }
  }
  saving.value = true;
  try {
    const result = await updateResumeParse(props.resumeId, payload);
    emit('update:open', false);
    emit('saved', result);
    message.success('人工修正已保存');
  } catch (e) {
    message.error(errorText(e, '人工修正保存失败'));
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <a-modal
    :open="open"
    title="人工修正解析结果"
    :confirm-loading="saving"
    :mask-closable="false"
    ok-text="保存"
    cancel-text="取消"
    @ok="handleSubmit"
    @update:open="emit('update:open', $event)"
  >
    <a-form layout="vertical">
      <a-form-item label="姓名" required>
        <a-input v-model:value="form.parsedName" placeholder="请输入姓名" />
      </a-form-item>
      <a-form-item label="手机号">
        <a-input v-model:value="form.parsedMobile" placeholder="请输入手机号" />
      </a-form-item>
      <a-form-item label="邮箱">
        <a-input v-model:value="form.parsedEmail" placeholder="请输入邮箱" />
      </a-form-item>
      <a-form-item label="教育经历（JSON 数组）">
        <a-textarea v-model:value="form.educationText" :rows="5" placeholder='[{"school":"...","major":"..."}]' />
      </a-form-item>
      <a-form-item label="工作经历（JSON 数组）">
        <a-textarea v-model:value="form.experienceText" :rows="5" placeholder='[{"company":"...","positionName":"..."}]' />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
