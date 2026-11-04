<script setup lang="ts">
/**
 * 管理端-数据字典类型：维护租户级字典分类（如 education / hire-type / resign-reason）。
 * 字典项在「字典项」页按类型维护。
 */
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import { createDictType, listDictTypes, updateDictType } from '../../../api/platform';
import type { CrudColumn, CrudField, SimpleCrudApi } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'code', title: '类型编码', width: 200 },
  { key: 'name', title: '类型名称', width: 200 },
  { key: 'updatedAt', title: '更新时间', width: 180, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'code', label: '类型编码', type: 'text', required: true, placeholder: '如 hire-type' },
  { key: 'name', label: '类型名称', type: 'text', required: true, placeholder: '如 雇佣类型' },
];

const api: SimpleCrudApi = {
  list: listDictTypes,
  create: createDictType,
  update: updateDictType,
};
</script>

<template>
  <SimpleCrudPage
    title="字典类型"
    subtitle="维护字典分类；类型编码为业务引用键，创建后不建议修改"
    perm-prefix="plt:dict"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
