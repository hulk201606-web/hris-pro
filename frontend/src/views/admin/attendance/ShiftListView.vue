<script setup lang="ts">
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import { createShift, listShifts, updateShift } from '../../../api/attendance';
import type { CrudColumn, CrudField, SimpleCrudApi } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'name', title: '班次名称', width: 160 },
  { key: 'code', title: '班次编码', width: 140 },
  { key: 'startTime', title: '上班时间', width: 110, type: 'time' },
  { key: 'endTime', title: '下班时间', width: 110, type: 'time' },
  { key: 'flexible', title: '类型', width: 110, type: 'tag', valueMap: { '0': '固定', '1': '弹性' } },
  { key: 'crossDay', title: '跨天', width: 90, type: 'tag', valueMap: { true: '是', false: '否' } },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'name', label: '班次名称', type: 'text', required: true, placeholder: '如 标准白班' },
  { key: 'code', label: '班次编码', type: 'text', placeholder: '留空由服务端生成' },
  { key: 'startTime', label: '上班时间', type: 'time', required: true },
  { key: 'endTime', label: '下班时间', type: 'time', required: true },
  {
    key: 'flexible',
    label: '班次类型',
    type: 'select',
    options: [
      { label: '固定班', value: 0 },
      { label: '弹性班', value: 1 },
    ],
  },
];

const api: SimpleCrudApi = {
  list: listShifts,
  create: createShift,
  update: updateShift,
};
</script>

<template>
  <SimpleCrudPage
    title="班次"
    subtitle="维护固定/弹性/跨天班次；下班时间不晚于上班时间即视为跨天班次"
    perm-prefix="att:shift"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
