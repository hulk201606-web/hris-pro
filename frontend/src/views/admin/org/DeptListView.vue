<script setup lang="ts">
/**
 * 管理端-部门管理：部门树维护（名称 / 编码 / 上级部门 / 负责人 / 生效日期）。
 *
 * 后端 GET /org/depts 支持 tree 参数；此处以扁平列表 + 物化路径展示层级，
 * 因为 SimpleCrudPage 的表格不支持树形展开。层级关系由 path 列（如 1.3.7）体现，
 * 上级部门通过 parentId 指定，留空表示根节点。
 * 服务端在新建/更新时校验树深度（≤8 层）与编码唯一。
 */
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import { createDept, listDepts, updateDept } from '../../../api/org';
import type { CrudColumn, CrudField, SimpleCrudApi } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'path', title: '层级路径', width: 130 },
  { key: 'name', title: '部门名称', width: 180 },
  { key: 'code', title: '部门编码', width: 140 },
  { key: 'parentId', title: '上级部门 ID', width: 120 },
  { key: 'managerEmpId', title: '负责人 ID', width: 120 },
  { key: 'status', title: '状态', width: 90, type: 'tag', valueMap: { '1': '启用', '0': '停用' } },
  { key: 'effectiveFrom', title: '生效日期', width: 120, type: 'date' },
];

const fields: CrudField[] = [
  { key: 'name', label: '部门名称', type: 'text', required: true },
  { key: 'code', label: '部门编码', type: 'text', placeholder: '留空由服务端自动分配' },
  { key: 'parentId', label: '上级部门 ID', type: 'number', placeholder: '留空表示根节点' },
  { key: 'managerEmpId', label: '部门负责人 ID', type: 'number', placeholder: '员工 ID，可留空' },
  { key: 'effectiveFrom', label: '生效日期', type: 'date', placeholder: '留空为立即生效' },
  {
    key: 'status',
    label: '状态',
    type: 'select',
    required: true,
    options: [
      { label: '启用', value: 1 },
      { label: '停用', value: 0 },
    ],
  },
];

const api: SimpleCrudApi = {
  list: () => listDepts(false),
  create: createDept,
  update: updateDept,
};
</script>

<template>
  <SimpleCrudPage
    title="部门管理"
    subtitle="维护部门树（深度不超过 8 层）；层级由物化路径体现，新建子部门需指定上级部门 ID"
    perm-prefix="org:dept"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
