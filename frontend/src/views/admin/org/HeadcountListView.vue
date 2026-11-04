<script setup lang="ts">
/**
 * 管理端-编制看板：按「部门 × 岗位 × 期间」查看配额、占用、空缺与占用率，
 * 并可直接设置配额。
 *
 * 注意：后端 PUT /org/headcounts 是 upsert（不带 id，主键由
 * deptId + positionId + period 三元组确定），因此编辑时提交的 payload
 * 必须包含这三个字段——编辑表单里保留它们正是为此。
 * 空缺可为负值，表示超编；占用率在 quota<=0 时为 0（服务端除零守卫）。
 */
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import { listHeadcounts, updateHeadcount } from '../../../api/org';
import type { CrudColumn, CrudField, SimpleCrudApi } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'deptId', title: '部门 ID', width: 110 },
  { key: 'positionId', title: '岗位 ID', width: 110 },
  { key: 'period', title: '期间', width: 110 },
  { key: 'quota', title: '编制数', width: 100, type: 'number' },
  { key: 'occupied', title: '已占用', width: 100, type: 'number' },
  { key: 'vacancy', title: '空缺', width: 100, type: 'number' },
  { key: 'usageRate', title: '占用率', width: 110, type: 'number' },
];

const fields: CrudField[] = [
  { key: 'deptId', label: '部门 ID', type: 'number', required: true },
  { key: 'positionId', label: '岗位 ID', type: 'number', required: true },
  { key: 'period', label: '期间', type: 'text', required: true, placeholder: '如 2026Q4' },
  { key: 'quota', label: '编制数', type: 'number', required: true, min: 0 },
];

const api: SimpleCrudApi = {
  list: listHeadcounts,
  // PUT /org/headcounts 以 deptId+positionId+period 定位记录，不需要路径 id
  update: (_id: number, data: any) => updateHeadcount(data),
};
</script>

<template>
  <SimpleCrudPage
    title="编制管理"
    subtitle="按部门 × 岗位 × 期间设置编制配额；空缺为负表示超编，超编入职会被服务端拦截"
    perm-prefix="org:headcount"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
