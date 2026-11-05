<script setup lang="ts">

/**
 * 管理端-薪酬核算批次列表：CRUD（周期 / 薪酬组 / 状态 / 核算人），
 * 走 EntityCrudPage 组件 + /cmp/payroll-runs 接口；
 * 页头提供「算薪监控」入口跳转至逐员工任务监控页。
 * 状态机（后端 cmp_payroll_run.status）：INIT/TRIAL/CALC/CALCULATING/REVIEW/APPROVE/PAID/LOCK，
 * 状态不入参（由 submit/审批回调/计算动作迁移，批 8 计算中 CALCULATING 异步流转）。
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

/** 状态 → 中文文案（列表 tag 与表单下拉共用） */
const STATUS_MAP: Record<string, string> = {
  INIT: '草稿',
  TRIAL: '试算',
  CALCULATING: '计算中',
  CALC: '已计算',
  REVIEW: '待复核',
  APPROVE: '已审批',
  PAID: '已支付',
  LOCK: '已锁定',
};

const columns: CrudColumn[] = [
  { key: 'periodCode', title: '薪酬周期', width: 130 },
  { key: 'payGroupId', title: '薪酬组 ID', width: 120 },
  { key: 'status', title: '状态', width: 130, type: 'tag', valueMap: STATUS_MAP },
  { key: 'calcBy', title: '核算人 ID', width: 120 },
  { key: 'reviewBy', title: '复核人 ID', width: 120 },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'periodCode', label: '薪酬周期', type: 'text', required: true, placeholder: '如 2026-10' },
  { key: 'payGroupId', label: '薪酬组 ID', type: 'number', required: true, min: 1 },
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
