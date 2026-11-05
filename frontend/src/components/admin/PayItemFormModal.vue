<script setup lang="ts">
/**
 * 薪资项新建/编辑弹窗：name/code/category/seq/formula/status 表单 +
 * 「校验」按钮（调 /cmp/formulas/validate 编译校验与试算，结果就地展示）。
 * 编辑态 code 只读（后端更新请求不含 code/payGroupId）。
 */
import { reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { SafetyOutlined } from '@ant-design/icons-vue';
import { errorText } from './crud-helpers';
import { createPayItem, updatePayItem, validateFormula } from '../../api/compensation';
import type { PayItem } from '../../types/api-batch3';

const props = defineProps<{
  open: boolean;
  mode: 'create' | 'edit';
  editing: PayItem | null;
  payGroupId: number | null;
}>();

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'saved'): void;
}>();

const CATEGORY_OPTIONS = [
  { label: '应发项 EARNING', value: 'EARNING' },
  { label: '扣款项 DEDUCTION', value: 'DEDUCTION' },
  { label: '个税 TAX', value: 'TAX' },
  { label: '净额 NET', value: 'NET' },
];

const submitting = ref(false);
const validating = ref(false);
const validateResult = ref<{ valid: boolean; message: string; result?: string | null } | null>(null);

const form = reactive({
  name: '',
  code: '',
  category: 'EARNING',
  seq: 1,
  formula: '',
  status: 1,
  sampleVars: '',
});

watch(
  () => props.open,
  (open) => {
    if (!open) return;
    validateResult.value = null;
    if (props.mode === 'edit' && props.editing) {
      Object.assign(form, {
        name: props.editing.name,
        code: props.editing.code,
        category: String(props.editing.category),
        seq: props.editing.seq ?? 1,
        formula: props.editing.formula ?? '',
        status: props.editing.status ?? 1,
        sampleVars: '',
      });
    } else {
      Object.assign(form, {
        name: '',
        code: '',
        category: 'EARNING',
        seq: 1,
        formula: '',
        status: 1,
        sampleVars: '',
      });
    }
  },
);

/** 弹窗内「校验」：编译 + 试算，结果就地展示（不落库） */
async function handleValidate() {
  const formula = form.formula.trim();
  if (!formula) {
    message.warning('请先填写公式');
    return;
  }
  let sampleVars: Record<string, unknown> | undefined;
  const raw = form.sampleVars.trim();
  if (raw) {
    try {
      const parsed: unknown = JSON.parse(raw);
      if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
        throw new Error('不是 JSON 对象');
      }
      sampleVars = parsed as Record<string, unknown>;
    } catch {
      message.warning('试算样例变量须为 JSON 对象，如 {"base":10000}');
      return;
    }
  }
  validating.value = true;
  try {
    validateResult.value = await validateFormula(formula, sampleVars);
  } catch (e) {
    validateResult.value = { valid: false, message: errorText(e, '校验请求失败') };
  } finally {
    validating.value = false;
  }
}

async function handleSubmit() {
  if (!form.name.trim() || !form.code.trim()) {
    message.warning('名称与编码必填');
    return;
  }
  if (props.mode === 'create' && !props.payGroupId) {
    message.warning('请先选择薪酬组');
    return;
  }
  submitting.value = true;
  try {
    if (props.mode === 'create') {
      await createPayItem({
        payGroupId: props.payGroupId,
        name: form.name.trim(),
        code: form.code.trim(),
        category: form.category,
        seq: form.seq,
        formula: form.formula.trim() || null,
        status: form.status,
      });
      message.success('薪资项已创建');
    } else {
      await updatePayItem(props.editing?.id as number, {
        name: form.name.trim(),
        category: form.category,
        seq: form.seq,
        formula: form.formula.trim() || null,
        status: form.status,
      });
      message.success('薪资项已更新');
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
    :title="mode === 'create' ? '新建薪资项' : `编辑 ${form.name || '薪资项'}`"
    :confirm-loading="submitting"
    :mask-closable="false"
    width="640px"
    ok-text="保存"
    cancel-text="取消"
    @update:open="emit('update:open', $event)"
    @ok="handleSubmit"
  >
    <a-form layout="vertical">
      <a-row :gutter="12">
        <a-col :span="12">
          <a-form-item label="名称" required>
            <a-input v-model:value="form.name" placeholder="如 基本工资" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="编码" required>
            <a-input v-model:value="form.code" :disabled="mode === 'edit'" placeholder="如 baseSalary" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-row :gutter="12">
        <a-col :span="8">
          <a-form-item label="类别" required>
            <a-select v-model:value="form.category" :options="CATEGORY_OPTIONS" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="顺序">
            <a-input-number v-model:value="form.seq" :min="1" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="状态">
            <a-select
              v-model:value="form.status"
              :options="[{ label: '启用', value: 1 }, { label: '停用', value: 0 }]"
            />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="公式（Aviator 表达式，可留空表示固定 0）">
        <a-textarea v-model:value="form.formula" :rows="3" placeholder="如 round(base * 0.1, 2)" />
      </a-form-item>
      <a-form-item label="试算样例变量（JSON，校验时使用；前序项编码可在此给样例值）">
        <a-input v-model:value="form.sampleVars" placeholder='如 {"base":10000,"bonus":2000}' />
      </a-form-item>
      <a-button :loading="validating" @click="handleValidate">
        <template #icon><SafetyOutlined /></template>
        校验
      </a-button>
      <a-alert
        v-if="validateResult"
        :type="validateResult.valid ? 'success' : 'error'"
        :message="validateResult.message"
        :description="validateResult.valid && validateResult.result ? `试算结果：${validateResult.result}` : ''"
        show-icon
        class="validate-alert"
      />
    </a-form>
  </a-modal>
</template>

<style scoped>
.validate-alert {
  margin-top: 12px;
}
</style>
