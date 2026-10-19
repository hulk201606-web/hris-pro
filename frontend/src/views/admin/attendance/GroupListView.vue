<script setup lang="ts">

/**
 * 管理端-考勤组列表：简单 CRUD（考勤组名称 / 编码 / 组长），
 * 走 SimpleCrudPage 组件 + /att/groups 接口。
 */
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import { createGroup, listGroups, updateGroup } from '../../../api/attendance';
import type { CrudColumn, CrudField, SimpleCrudApi } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'name', title: '班组名称', width: 180 },
  { key: 'code', title: '班组编码', width: 160 },
  { key: 'leaderEmpId', title: '组长员工 ID', width: 130 },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'name', label: '班组名称', type: 'text', required: true, placeholder: '如 研发中心' },
  { key: 'code', label: '班组编码', type: 'text', placeholder: '留空由服务端生成' },
  { key: 'leaderEmpId', label: '组长员工 ID', type: 'number', min: 1 },
];

const api: SimpleCrudApi = {
  list: listGroups,
  create: createGroup,
  update: updateGroup,
};
</script>

<template>
  <SimpleCrudPage
    title="考勤组"
    subtitle="维护考勤班组与组长，班组用于绑定考勤政策与排班规则"
    perm-prefix="att:group"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
