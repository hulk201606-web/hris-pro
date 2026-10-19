<script setup lang="ts">

/**
 * 管理端-目标列表：CRUD（目标标题 / 周期 / 员工 / 权重），
 * 走 EntityCrudPage 组件 + /prf/goals 接口。
 */
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import {
  createGoal,
  deleteGoal,
  listGoals,
  updateGoal,
} from '../../../api/performance';
import type { CrudApi, CrudColumn, CrudField } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'title', title: '目标' },
  { key: 'cycleId', title: '周期 ID', width: 110 },
  { key: 'empId', title: '员工 ID', width: 110 },
  { key: 'weight', title: '权重(%)', width: 110, type: 'number' },
  { key: 'targetValue', title: '目标值', width: 140 },
  { key: 'resultValue', title: '实际值', width: 140 },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'cycleId', label: '周期 ID', type: 'number', required: true, min: 1 },
  { key: 'empId', label: '员工 ID', type: 'number', required: true, min: 1 },
  { key: 'title', label: '目标', type: 'text', required: true, placeholder: '如 核心系统可用性提升' },
  { key: 'weight', label: '权重(%)', type: 'number', required: true, min: 0 },
  { key: 'targetValue', label: '目标值', type: 'text', placeholder: '如 99.95%' },
  { key: 'resultValue', label: '实际值', type: 'text', placeholder: '如 99.93%' },
];

const api: CrudApi = {
  list: listGoals,
  create: createGoal,
  update: updateGoal,
  remove: deleteGoal,
};
</script>

<template>
  <EntityCrudPage
    title="目标管理"
    subtitle="维护员工在考核周期内的目标、权重与达成情况"
    perm-prefix="prf:goal"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
