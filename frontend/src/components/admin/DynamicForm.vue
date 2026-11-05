<script setup lang="ts">
/**
 * 动态表单渲染器（C11）：按 JSON Schema 渲染审批表单，供任务审批弹窗使用。
 *
 * 支持的类型映射：
 *   string               -> a-input
 *   number / integer     -> a-input-number
 *   enum（有 enum 字段） -> a-select
 *   boolean              -> a-switch
 *   多行（format=textarea 或 title 含「说明」）-> a-textarea
 *   未知类型             -> 降级 a-input
 *
 * schema 形如 { type: 'object', properties: { field: { type, title, enum, format } }, required: [] }；
 * 也兼容直接给 properties 的写法。model 由父组件持有，本组件只做双向同步。
 */
import { computed } from 'vue';

interface SchemaField {
  /** 字段名（即 formValues 的键） */
  key: string;
  label: string;
  kind: 'input' | 'textarea' | 'number' | 'select' | 'switch';
  options: { label: string; value: string | number | boolean }[];
  required: boolean;
  defaultValue: unknown;
}

const props = defineProps<{
  /** 已解析的 JSON Schema 对象 */
  schema: Record<string, unknown> | null;
  modelValue: Record<string, unknown>;
}>();

const emit = defineEmits<{ 'update:modelValue': [Record<string, unknown>] }>();

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {};
}

const fields = computed<SchemaField[]>(() => {
  if (!props.schema) return [];
  const root = asRecord(props.schema);
  const propsMap = asRecord(root.properties);
  const requiredList = Array.isArray(root.required) ? (root.required as unknown[]) : [];
  return Object.entries(propsMap).map(([key, raw]) => {
    const f = asRecord(raw);
    const type = typeof f.type === 'string' ? f.type : '';
    const title = typeof f.title === 'string' ? f.title : key;
    const format = typeof f.format === 'string' ? f.format : '';
    let kind: SchemaField['kind'] = 'input';
    if (Array.isArray(f.enum)) {
      kind = 'select';
    } else if (type === 'boolean') {
      kind = 'switch';
    } else if (type === 'number' || type === 'integer') {
      kind = 'number';
    } else if (format === 'textarea' || title.includes('说明')) {
      kind = 'textarea';
    } else if (type !== 'string') {
      // 未知类型降级为普通输入
      kind = 'input';
    }
    const options = Array.isArray(f.enum)
      ? (f.enum as unknown[]).map((v) => ({
          label: String(v),
          value: v as string | number | boolean,
        }))
      : [];
    return {
      key,
      label: title,
      kind,
      options,
      required: requiredList.includes(key),
      defaultValue: kind === 'switch' ? false : kind === 'number' ? undefined : '',
    };
  });
});

function setValue(key: string, v: unknown) {
  emit('update:modelValue', { ...props.modelValue, [key]: v });
}

const toValue = (v: unknown): unknown => v;
</script>

<template>
  <a-form v-if="fields.length" layout="vertical" class="dynamic-form">
    <a-form-item v-for="f in fields" :key="f.key" :label="f.label" :required="f.required">
      <a-select
        v-if="f.kind === 'select'"
        :value="(modelValue[f.key] as string | number | undefined) ?? undefined"
        :options="f.options"
        allow-clear
        :placeholder="`请选择${f.label}`"
        style="width: 100%"
        @change="(v: unknown) => setValue(f.key, v)"
      />
      <a-switch
        v-else-if="f.kind === 'switch'"
        :checked="Boolean(modelValue[f.key])"
        @change="(v: unknown) => setValue(f.key, v)"
      />
      <a-input-number
        v-else-if="f.kind === 'number'"
        :value="modelValue[f.key] as number | undefined"
        style="width: 100%"
        :placeholder="`请输入${f.label}`"
        @update:value="(v: unknown) => setValue(f.key, v)"
      />
      <a-textarea
        v-else-if="f.kind === 'textarea'"
        :value="(modelValue[f.key] as string) ?? ''"
        :rows="3"
        :placeholder="`请输入${f.label}`"
        @update:value="(v: unknown) => setValue(f.key, v)"
      />
      <a-input
        v-else
        :value="(modelValue[f.key] as string) ?? ''"
        :placeholder="`请输入${f.label}`"
        @update:value="(v: unknown) => setValue(f.key, v)"
      />
    </a-form-item>
  </a-form>
</template>

<style scoped>
.dynamic-form {
  margin-bottom: 8px;
}
</style>
