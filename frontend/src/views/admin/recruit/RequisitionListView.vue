<script setup lang="ts">

/**
 * 管理端-职位需求列表：CRUD（需求编号 / 部门 / 岗位 / 招聘人数），
 * 走 EntityCrudPage 组件 + /rcu/requisitions 接口。
 */
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import {
  createRequisition,
  deleteRequisition,
  listRequisitions,
  updateRequisition,
} from '../../../api/recruit';
import type { CrudApi, CrudColumn, CrudField } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'reqNo', title: '需求编号', width: 160 },
  { key: 'deptId', title: '部门 ID', width: 110 },
  { key: 'positionId', title: '岗位 ID', width: 110 },
  { key: 'headcount', title: '招聘人数', width: 110, type: 'number' },
  { key: 'status', title: '状态', width: 120, type: 'tag' },
  { key: 'reason', title: '需求原因' },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'reqNo', label: '需求编号', type: 'text', required: true, placeholder: '如 REQ-2026-0014' },
  { key: 'deptId', label: '部门 ID', type: 'number', required: true, min: 1 },
  { key: 'positionId', label: '岗位 ID', type: 'number', required: true, min: 1 },
  { key: 'headcount', label: '招聘人数', type: 'number', required: true, min: 1 },
  { key: 'reason', label: '需求原因', type: 'textarea' },
  {
    key: 'status',
    label: '状态',
    type: 'select',
    required: true,
    options: [
      { label: '待审批', value: 'PENDING' },
      { label: '已审批', value: 'APPROVED' },
      { label: '已关闭', value: 'CLOSED' },
    ],
  },
];

const api: CrudApi = {
  list: listRequisitions,
  create: createRequisition,
  update: updateRequisition,
  remove: deleteRequisition,
};
</script>

<template>
  <EntityCrudPage
    title="职位需求"
    subtitle="维护招聘需求单，审批通过后进入职位发布与候选人筛选"
    perm-prefix="rcu:requisition"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
