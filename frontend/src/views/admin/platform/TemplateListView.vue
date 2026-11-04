<script setup lang="ts">
/**
 * 管理端-通知模板：站内信 / 邮件 / Webhook 的模板维护。
 * 模板正文支持 {param} 占位符，渲染时参数为 null 会替换为空串。
 */
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import { createTemplate, listTemplates, updateTemplate } from '../../../api/platform';
import type { CrudColumn, CrudField, SimpleCrudApi } from '../../../types/admin';

const channelOptions = [
  { label: '站内信', value: 'INBOX' },
  { label: '邮件', value: 'EMAIL' },
  { label: 'Webhook', value: 'WEBHOOK' },
];

const columns: CrudColumn[] = [
  { key: 'code', title: '模板编码', width: 180 },
  {
    key: 'channel',
    title: '渠道',
    width: 110,
    type: 'tag',
    valueMap: { INBOX: '站内信', EMAIL: '邮件', WEBHOOK: 'Webhook' },
  },
  { key: 'subject', title: '标题', width: 200 },
  { key: 'content', title: '正文' },
  { key: 'updatedAt', title: '更新时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'code', label: '模板编码', type: 'text', required: true, placeholder: '如 ATT_LEAVE_APPROVED' },
  { key: 'channel', label: '渠道', type: 'select', required: true, options: channelOptions },
  { key: 'subject', label: '标题', type: 'text', placeholder: '邮件渠道使用' },
  {
    key: 'content',
    label: '正文',
    type: 'textarea',
    required: true,
    placeholder: '支持 {param} 占位符，如 您有一条 {bizType} 待审批',
  },
];

const api: SimpleCrudApi = {
  list: listTemplates,
  create: createTemplate,
  update: updateTemplate,
};
</script>

<template>
  <SimpleCrudPage
    title="通知模板"
    subtitle="按渠道 + 编码唯一；正文支持 {param} 占位符，参数为空时渲染为空串"
    perm-prefix="plt:notification"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
