<script setup lang="ts">

/**
 * 管理端-员工薪酬档列表：CRUD（员工 ID / 薪酬组 / 薪级 / 生效日期），
 * 走 EntityCrudPage 组件 + /cmp/employee-pays 接口。
 * 行操作「调薪轨迹」：按 empId 拉取版本链与调整单时间线（CMP-F-018）。
 */
import { ref } from 'vue';
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import PayHistoryDrawer from '../../../components/admin/compensation/PayHistoryDrawer.vue';
import {
  createEmployeePay,
  deleteEmployeePay,
  listEmployeePays,
  updateEmployeePay,
} from '../../../api/compensation';
import type { CrudApi, CrudColumn, CrudField, CrudRow } from '../../../types/admin';

const historyOpen = ref(false);
const historyEmpId = ref<number | null>(null);

function openHistory(record: CrudRow) {
  historyEmpId.value = Number(record.empId);
  historyOpen.value = true;
}

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
  >
    <template #rowActions="{ record }">
      <a-button type="link" size="small" @click="openHistory(record)">调薪轨迹</a-button>
    </template>
  </EntityCrudPage>

  <PayHistoryDrawer v-model:open="historyOpen" :emp-id="historyEmpId" />
</template>
