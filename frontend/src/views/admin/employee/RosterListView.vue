<script setup lang="ts">
/**
 * 管理端-员工花名册：多维筛选 + 分页查看员工简报（EMP-F-022）。
 *
 * 说明：
 * - 列表为脱敏简报，证件号与手机号只给掩码，明文不下发（EMP-F-025）；
 * - 编辑仅覆盖基础字段（姓名/邮箱/性别/生日）。服务端 update 是局部更新
 *   （null 字段不覆盖），且子档案 replace 在 list 为 null 时直接返回，
 *   因此本表单不提交 educations/works/families/emergencies 不会清空子档案；
 * - 本页不提供"新建"：新员工请在「员工异动 → 入职办理」走入职流程，
 *   那里才有编制校验与状态机流转。
 * - 不提供"删除"：员工不做物理删除，离职走状态机。
 */
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import { listRoster, updateEmployee } from '../../../api/employee';
import type { CrudApi, CrudColumn, CrudField, CrudRow } from '../../../types/admin';
import type { PageResult } from '../../../types/api';

const columns: CrudColumn[] = [
  { key: 'empNo', title: '工号', width: 120 },
  { key: 'name', title: '姓名', width: 100 },
  { key: 'statusLabel', title: '状态', width: 100, type: 'tag' },
  { key: 'deptId', title: '部门 ID', width: 100 },
  { key: 'positionId', title: '岗位 ID', width: 100 },
  { key: 'mobileMask', title: '手机号(脱敏)', width: 140 },
  { key: 'certNoMask', title: '证件号(脱敏)', width: 180 },
  { key: 'hireType', title: '雇佣类型', width: 110 },
  { key: 'email', title: '邮箱' },
];

const fields: CrudField[] = [
  { key: 'name', label: '姓名', type: 'text' },
  { key: 'email', label: '邮箱', type: 'text' },
  {
    key: 'gender',
    label: '性别',
    type: 'select',
    options: [
      { label: '男', value: 'M' },
      { label: '女', value: 'F' },
    ],
  },
  { key: 'birthDate', label: '出生日期', type: 'date' },
];

const api: CrudApi = {
  list: (page: number, size: number) => listRoster(page, size) as Promise<PageResult<CrudRow>>,
  update: (id: number, data: any) => updateEmployee(id, data),
};
</script>

<template>
  <EntityCrudPage
    title="员工花名册"
    subtitle="按状态 / 部门 / 岗位查看在职人员；证件号与手机号默认脱敏，敏感字段变更会落审计"
    perm-prefix="emp:employee"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
