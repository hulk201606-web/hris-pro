<script setup lang="ts">
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import {
  createEmployeePay,
  deleteEmployeePay,
  listEmployeePays,
  updateEmployeePay,
} from '../../../api/compensation';
import type { CrudApi, CrudColumn, CrudField } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'empId', title: '员工 ID', width: 120 },
  { key: 'payGroupId', title: '薪酬组 ID', width: 120 },
  { key: 'payGradeId', title: '薪级 ID', width: 110 },
  { key: 'effectiveFrom', title: '生效日期', width: 130, type: 'date' },
  { key: 'effectiveTo', title: '失效日期', width: 130, type: 'date' },
  { key: 'salaryAmountMask', title: '薪资(脱敏)', width: 130 },
  { key: 'bankAccountMask', title: '银行账号(脱敏)', width: 170 },
];

const fields: CrudField[] = [
  { key: 'empId', label: '员工 ID', type: 'number', required: true, min: 1 },
  { key: 'payGroupId', label: '薪酬组 ID', type: 'number', required: true, min: 1 },
  { key: 'payGradeId', label: '薪级 ID', type: 'number', min: 1 },
  { key: 'effectiveFrom', label: '生效日期', type: 'date', required: true },
  { key: 'effectiveTo', label: '失效日期', type: 'date' },
  { key: 'salaryAmountCipher', label: '薪资', type: 'text', placeholder: 'MVP 阶段密文列存明文' },
  { key: 'salaryAmountMask', label: '薪资(脱敏)', type: 'text', placeholder: '如 22****' },
  { key: 'bankAccountCipher', label: '银行账号', type: 'text', placeholder: 'MVP 阶段密文列存明文' },
  { key: 'bankAccountMask', label: '银行账号(脱敏)', type: 'text', placeholder: '如 6222****2222' },
];

const api: CrudApi = {
  list: listEmployeePays,
  create: createEmployeePay,
  update: updateEmployeePay,
  remove: deleteEmployeePay,
};
</script>

<template>
  <EntityCrudPage
    title="员工薪酬"
    subtitle="维护员工薪酬档与银行账户，敏感字段按密文/掩码双列存储"
    perm-prefix="cmp:employee-pay"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
