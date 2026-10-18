<script setup lang="ts">
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import { createLeaveType, listLeaveTypes, updateLeaveType } from '../../../api/attendance';
import type { CrudColumn, CrudField, SimpleCrudApi } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'name', title: '假种名称', width: 150 },
  { key: 'code', title: '假种编码', width: 140 },
  { key: 'rules', title: '规则(JSON)' },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'name', label: '假种名称', type: 'text', required: true, placeholder: '如 年假' },
  { key: 'code', label: '假种编码', type: 'text', placeholder: '留空由服务端生成' },
  {
    key: 'rules',
    label: '规则(JSON)',
    type: 'textarea',
    placeholder: '如 {"paid":true,"needProof":false,"minUnit":"0.5d"}',
  },
];

const api: SimpleCrudApi = {
  list: listLeaveTypes,
  create: createLeaveType,
  update: updateLeaveType,
};
</script>

<template>
  <SimpleCrudPage
    title="假期类型"
    subtitle="配置假种与规则（是否带薪/是否需证明/最小请假单位/有效期/结转上限）"
    perm-prefix="att:leave:type"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
