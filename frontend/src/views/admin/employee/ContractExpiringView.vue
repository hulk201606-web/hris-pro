<script setup lang="ts">
/**
 * 管理端-合同到期预警：列出未来窗口内到期的合同（EMP-F-015）。
 *
 * 服务端按剩余天数分级：≤7 天 LEVEL_7、≤30 天 LEVEL_30、其余 LEVEL_60；
 * daysToExpire <= 0 表示已过期。未建续签单的合同会在预警窗口内持续出现，
 * 由 HR 在合同台账中处理续签。
 */
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import { listExpiringContracts } from '../../../api/employee';
import type { CrudColumn, CrudField, SimpleCrudApi } from '../../../types/admin';

const columns: CrudColumn[] = [
  { key: 'empNo', title: '工号', width: 120 },
  { key: 'name', title: '姓名', width: 110 },
  { key: 'contractNo', title: '合同编号', width: 160 },
  { key: 'endDate', title: '到期日', width: 120, type: 'date' },
  { key: 'daysToExpire', title: '剩余天数', width: 110, type: 'number' },
  {
    key: 'level',
    title: '预警级别',
    width: 120,
    type: 'tag',
    valueMap: { LEVEL_7: '7 天内', LEVEL_30: '30 天内', LEVEL_60: '60 天内' },
  },
];

// 纯只读看板：不提供新建/编辑，fields 传空数组满足组件契约
const fields: CrudField[] = [];

const api: SimpleCrudApi = {
  list: () => listExpiringContracts(60),
};
</script>

<template>
  <SimpleCrudPage
    title="合同到期预警"
    subtitle="未来 60 天内到期的劳动合同；7 天内为最高优先级，请及时发起续签或终止"
    perm-prefix="emp:contract"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
