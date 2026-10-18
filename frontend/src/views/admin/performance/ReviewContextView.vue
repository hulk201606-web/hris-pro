<script setup lang="ts">
import EmployeeContextPanel from '../../../components/admin/EmployeeContextPanel.vue';
import { fetchReviewContext } from '../../../api/performance';
</script>

<template>
  <EmployeeContextPanel
    title="评估上下文"
    subtitle="评估前按员工 ID 拉取档案简报（部门/汇报线/在职状态），经绩效到员工的内部接口跨域取数，不在绩效域冗余存储。"
    :fetcher="fetchReviewContext"
  />
</template>
