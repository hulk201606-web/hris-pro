<script setup lang="ts">
/**
 * BPMN 设计器工具栏：版本信息 + 校验 / 保存 / 部署。
 * 校验结果就地展示 errors；部署前要求无未保存修改（由父组件拦截）。
 */
import { SaveOutlined, CheckCircleOutlined, CloudUploadOutlined, ArrowLeftOutlined } from '@ant-design/icons-vue';
import type { ModelValidateResult } from '../../../types/api-batch9';

defineProps<{
  modelId: number;
  version: number;
  deployState: string;
  dirty: boolean;
  busy: boolean;
  validating: boolean;
  canWrite: boolean;
  validation: ModelValidateResult | null;
}>();

const emit = defineEmits<{
  back: [];
  validate: [];
  save: [];
  deploy: [];
}>();
</script>

<template>
  <div class="toolbar">
    <div class="left">
      <a-button type="text" @click="emit('back')">
        <template #icon><ArrowLeftOutlined /></template>
        返回模型列表
      </a-button>
      <a-divider type="vertical" />
      <span class="id">模型 #{{ modelId }}</span>
      <a-tag color="blue">v{{ version }}</a-tag>
      <a-tag :color="deployState === 'DEPLOYED' ? 'green' : 'default'">
        {{ deployState === 'DEPLOYED' ? '已部署' : '草稿' }}
      </a-tag>
      <a-badge v-if="dirty" status="warning" text="有未保存修改" />
    </div>
    <a-space>
      <a-button :loading="validating" :disabled="busy || !canWrite" @click="emit('validate')">
        <template #icon><CheckCircleOutlined /></template>
        校验
      </a-button>
      <a-button type="primary" :loading="busy" :disabled="!canWrite" @click="emit('save')">
        <template #icon><SaveOutlined /></template>
        保存
      </a-button>
      <a-button :disabled="busy || !canWrite" @click="emit('deploy')">
        <template #icon><CloudUploadOutlined /></template>
        部署
      </a-button>
    </a-space>

    <div v-if="validation" class="result">
      <a-alert
        v-if="validation.valid"
        type="success"
        show-icon
        message="校验通过：BPMN 结构合法，可保存或部署"
      />
      <a-alert v-else type="error" show-icon message="校验未通过">
        <template #description>
          <ul class="errors">
            <li v-for="(err, i) in validation.errors" :key="i">{{ err }}</li>
          </ul>
        </template>
      </a-alert>
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: #fff;
  border-bottom: 1px solid var(--color-border, #f0f0f0);
}
.left {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: auto;
}
.id {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.result {
  flex-basis: 100%;
}
.errors {
  margin: 0;
  padding-left: 18px;
}
</style>
