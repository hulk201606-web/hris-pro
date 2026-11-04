<script setup lang="ts">
/**
 * 管理端-报表定义：维护报表目录与配置（defJson），供导出任务引用。
 * 导出任务在「导出记录」中查看（本页只维护定义）。
 */
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import { createReportDef, listReportDefs, updateReportDef } from '../../../api/platform';
import type { CrudColumn, CrudField, SimpleCrudApi } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'code', title: '报表编码', width: 180 },
  { key: 'name', title: '报表名称', width: 200 },
  { key: 'defJson', title: '配置(JSON)' },
  { key: 'updatedAt', title: '更新时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'name', label: '报表名称', type: 'text', required: true },
  { key: 'code', label: '报表编码', type: 'text', placeholder: '留空由服务端生成' },
  {
    key: 'defJson',
    label: '配置(JSON)',
    type: 'textarea',
    placeholder: '如 {"type":"headcount","groupBy":["dept"]}',
  },
];

const api: SimpleCrudApi = {
  list: listReportDefs,
  create: createReportDef,
  update: updateReportDef,
};
</script>

<template>
  <SimpleCrudPage
    title="报表定义"
    subtitle="维护报表目录与查询配置；导出任务引用此处的报表定义"
    perm-prefix="plt:report"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
