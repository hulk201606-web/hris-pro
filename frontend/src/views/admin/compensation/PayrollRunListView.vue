<script setup lang="ts">

/**
 * 管理端-薪酬核算批次列表：CRUD（周期 / 薪酬组 / 状态 / 核算人），
 * 走 EntityCrudPage 组件 + /cmp/payroll-runs 接口；
 * 页头提供「算薪监控」入口跳转至逐员工任务监控页。
 */
import { useRouter } from 'vue-router';
import { MonitorOutlined } from '@ant-design/icons-vue';
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import {
  createPayrollRun,
  deletePayrollRun,
  listPayrollRuns,
  updatePayrollRun,
} from '../../../api/compensation';
import type { CrudApi, CrudColumn, CrudField } from '../../../types/admin';

const router = useRouter();

const columns: CrudColumn[] = [
  { key: 'periodCode', title: '薪酬周期', width: 130 },
  { key: 'payGroupId', title: '薪酬组 ID', width: 120 },
  { key: 'status', title: '状态', width: 130, type: 'tag' },
  { key: 'calcBy', title: '核算人 ID', width: 120 },
  { key: 'reviewBy', title: '复核人 ID', width: 120 },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'periodCode', label: '薪酬周期', type: 'text', required: true, placeholder: '如 2026-10' },
  { key: 'payGroupId', label: '薪酬组 ID', type: 'number', required: true, min: 1 },
  {
    key: 'status',
    label: '状态',
    type: 'select',
    required: true,
    options: [
      { label: '草稿', value: 'DRAFT' },
      { label: '核算中', value: 'CALCULATING' },
      { label: '待复核', value: 'REVIEWING' },
      { label: '已封存', value: 'CLOSED' },
    ],
  },
  { key: 'calcBy', label: '核算人 ID', type: 'number', min: 1 },
  { key: 'reviewBy', label: '复核人 ID', type: 'number', min: 1 },
];

const api: CrudApi = {
  list: listPayrollRuns,
  create: createPayrollRun,
  update: updatePayrollRun,
  remove: deletePayrollRun,
};
</script>

<template>
  <EntityCrudPage
    title="薪酬核算"
    subtitle="按周期与薪酬组发起核算批次，跟踪核算到封存的状态流转"
    perm-prefix="cmp:payroll-run"
    :columns="columns"
    :fields="fields"
    :api="api"
  >
    <template #actions>
      <a-button @click="router.push('/admin/compensation/calc-monitor')">
        <template #icon><MonitorOutlined /></template>
        算薪监控
      </a-button>
    </template>
  </EntityCrudPage>
</template>
