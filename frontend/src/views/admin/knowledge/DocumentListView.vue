<script setup lang="ts">

/**
 * 管理端-文档管理列表：CRUD（标题 / 所属空间 / 文档类型 / 标题指纹），
 * 走 EntityCrudPage 组件 + /kb/documents 接口。
 */
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import {
  createDocument,
  deleteDocument,
  listDocuments,
  updateDocument,
} from '../../../api/knowledge';
import type { CrudApi, CrudColumn, CrudField } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'title', title: '文档标题' },
  { key: 'spaceId', title: '空间 ID', width: 110 },
  { key: 'docType', title: '文档类型', width: 130, type: 'tag' },
  { key: 'titleHash', title: '标题指纹', width: 150 },
  { key: 'fileId', title: '文件 ID', width: 110 },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'spaceId', label: '空间 ID', type: 'number', required: true, min: 1 },
  { key: 'title', label: '文档标题', type: 'text', required: true },
  { key: 'titleHash', label: '标题指纹', type: 'text', required: true, placeholder: '用于同标题去重' },
  {
    key: 'docType',
    label: '文档类型',
    type: 'select',
    required: true,
    options: [
      { label: '制度', value: 'POLICY' },
      { label: '规范', value: 'SPEC' },
      { label: '手册', value: 'MANUAL' },
      { label: '问答', value: 'FAQ' },
    ],
  },
  { key: 'fileId', label: '文件 ID', type: 'number', min: 1 },
];

const api: CrudApi = {
  list: listDocuments,
  create: createDocument,
  update: updateDocument,
  remove: deleteDocument,
};
</script>

<template>
  <EntityCrudPage
    title="文档管理"
    subtitle="维护空间下的知识文档元数据，文档分块与向量化在 RAG 域处理"
    perm-prefix="kb:document"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
