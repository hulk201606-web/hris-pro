<script setup lang="ts">

/**
 * 管理端-考核周期列表：CRUD（周期编码 / 方案 / 起止日期），
 * 走 EntityCrudPage 组件 + /prf/cycles 接口。
 */
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import {
  createCycle,
  deleteCycle,
  listCycles,
  updateCycle,
} from '../../../api/performance';
import type { CrudApi, CrudColumn, CrudField } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'periodCode', title: '周期编码', width: 130 },
  { key: 'planId', title: '方案 ID', width: 110 },
  { key: 'startDate', title: '开始日期', width: 130, type: 'date' },
  { key: 'endDate', title: '结束日期', width: 130, type: 'date' },
  { key: 'status', title: '状态', width: 120, type: 'tag' },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'planId', label: '方案 ID', type: 'number', required: true, min: 1 },
  { key: 'periodCode', label: '周期编码', type: 'text', required: true, placeholder: '如 2026-H2' },
  { key: 'startDate', label: '开始日期', type: 'date', required: true },
  { key: 'endDate', label: '结束日期', type: 'date', required: true },
  {
    key: 'status',
    label: '状态',
    type: 'select',
    required: true,
    options: [
      { label: '草稿', value: 'DRAFT' },
      { label: '进行中', value: 'RUNNING' },
      { label: '已结束', value: 'CLOSED' },
    ],
  },
];

const api: CrudApi = {
  list: listCycles,
  create: createCycle,
  update: updateCycle,
  remove: deleteCycle,
};
</script>

<template>
  <EntityCrudPage
    title="考核周期"
    subtitle="维护绩效周期与评估区间，周期驱动目标制定、评估与校准"
    perm-prefix="prf:cycle"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
