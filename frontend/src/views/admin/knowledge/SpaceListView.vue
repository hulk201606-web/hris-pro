<script setup lang="ts">

/**
 * 管理端-知识空间列表：CRUD（空间名称 / 编码 / 描述），
 * 走 EntityCrudPage 组件 + /kb/spaces 接口。
 */
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import {
  createSpace,
  deleteSpace,
  listSpaces,
  updateSpace,
} from '../../../api/knowledge';
import type { CrudApi, CrudColumn, CrudField } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'name', title: '空间名称', width: 160 },
  { key: 'code', title: '空间编码', width: 150 },
  { key: 'description', title: '描述' },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'name', label: '空间名称', type: 'text', required: true, placeholder: '如 人事制度库' },
  { key: 'code', label: '空间编码', type: 'text', required: true, placeholder: '如 hr-policy' },
  { key: 'description', label: '描述', type: 'textarea' },
];

const api: CrudApi = {
  list: listSpaces,
  create: createSpace,
  update: updateSpace,
  remove: deleteSpace,
};
</script>

<template>
  <EntityCrudPage
    title="知识空间"
    subtitle="按业务域划分知识空间，承载制度、规范与问答语料"
    perm-prefix="kb:space"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
