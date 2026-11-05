<script setup lang="ts">
/**
 * 面谈记录新建/编辑弹窗：conclusion 选 PIP 时 pip 起止日期必填联动（服务端同样校验，
 * 且 pip_status 旁路封死，只经 pip-result 迁移，故表单不提供该字段）。
 */
import { computed, reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { createInterview, updateInterview } from '../../../api/performance';
import { errorText } from '../../../components/admin/crud-helpers';
import type { InterviewRecord, InterviewConclusion } from '../../../types/api-batch2';

const props = defineProps<{
  open: boolean;
  mode: 'create' | 'edit';
  /** 编辑时传入行记录 */
  record?: InterviewRecord | null;
}>();

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'saved'): void;
}>();

const conclusionLabels: Record<string, string> = {
  IMPROVE: '改进',
  KEEP: '维持',
  PROMOTE: '晋升',
  PIP: '绩效改进',
};
const conclusionOptions = Object.entries(conclusionLabels).map(([value, label]) => ({ value, label }));

const submitting = ref(false);
const form = reactive({
  cycleId: undefined as number | undefined,
  empId: undefined as number | undefined,
  round: 1,
  interviewDate: '',
  interviewerEmpId: undefined as number | undefined,
  summary: '',
  conclusion: undefined as InterviewConclusion | undefined,
  pipStartDate: '',
  pipEndDate: '',
});

const isPip = computed(() => form.conclusion === 'PIP');
const modalTitle = computed(() => (props.mode === 'create' ? '新建面谈记录' : '编辑面谈记录'));

watch(
  () => props.open,
  (v) => {
    if (!v) return;
    if (props.mode === 'edit' && props.record) {
      const r = props.record;
      Object.assign(form, {
        cycleId: r.cycleId,
        empId: r.empId,
        round: r.round ?? 1,
        interviewDate: r.interviewDate?.slice(0, 10) ?? '',
        interviewerEmpId: r.interviewerEmpId,
        summary: r.summary ?? '',
        conclusion: r.conclusion as InterviewConclusion,
        pipStartDate: r.pipStartDate?.slice(0, 10) ?? '',
        pipEndDate: r.pipEndDate?.slice(0, 10) ?? '',
      });
    } else {
      Object.assign(form, {
        cycleId: undefined,
        empId: undefined,
        round: 1,
        interviewDate: '',
        interviewerEmpId: undefined,
        summary: '',
        conclusion: undefined,
        pipStartDate: '',
        pipEndDate: '',
      });
    }
  },
);

function validate(): string | null {
  if (!form.cycleId) return '请填写考核周期 ID';
  if (!form.empId) return '请填写员工 ID';
  if (!form.interviewDate) return '请选择面谈日期';
  if (!form.interviewerEmpId) return '请填写面谈人';
  if (!form.conclusion) return '请选择面谈结论';
  if (isPip.value && (!form.pipStartDate || !form.pipEndDate)) return '结论为绩效改进时，PIP 起止日期必填';
  return null;
}

async function handleSubmit() {
  const invalid = validate();
  if (invalid) {
    message.warning(invalid);
    return;
  }
  submitting.value = true;
  try {
    const payload: Record<string, unknown> = { ...form };
    if (!isPip.value) {
      delete payload.pipStartDate;
      delete payload.pipEndDate;
    }
    if (props.mode === 'create') {
      await createInterview(payload);
      message.success('面谈记录已创建');
    } else {
      await updateInterview(props.record?.id as number, payload);
      message.success('面谈记录已保存');
    }
    emit('update:open', false);
    emit('saved');
  } catch (e) {
    message.error(errorText(e, '保存失败'));
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <a-modal
    :open="open"
    :title="modalTitle"
    :confirm-loading="submitting"
    :mask-closable="false"
    ok-text="保存"
    cancel-text="取消"
    @ok="handleSubmit"
    @update:open="emit('update:open', $event)"
  >
    <a-form layout="vertical">
      <a-row :gutter="12">
        <a-col :span="12">
          <a-form-item label="考核周期 ID" required>
            <a-input-number v-model:value="form.cycleId" :min="1" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="员工 ID" required>
            <a-input-number v-model:value="form.empId" :min="1" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="轮次" required>
            <a-input-number v-model:value="form.round" :min="1" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="面谈日期" required>
            <a-date-picker v-model:value="form.interviewDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="面谈人（员工 ID）" required>
            <a-input-number v-model:value="form.interviewerEmpId" :min="1" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="面谈结论" required>
            <a-select v-model:value="form.conclusion" :options="conclusionOptions" placeholder="请选择" />
          </a-form-item>
        </a-col>
        <template v-if="isPip">
          <a-col :span="12">
            <a-form-item label="PIP 开始日期" required>
              <a-date-picker v-model:value="form.pipStartDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="PIP 结束日期" required>
              <a-date-picker v-model:value="form.pipEndDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
        </template>
      </a-row>
      <a-form-item label="面谈纪要">
        <a-textarea v-model:value="form.summary" :rows="4" placeholder="面谈要点与共识" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
