<script setup lang="ts">
/**
 * 管理端-职位管理：职位名称与所属序列。
 * 职位与岗位的区别：岗位偏序列与编制维度，职位偏具体职责描述。
 */
import CatalogCrudPage from '../../../components/admin/CatalogCrudPage.vue';
import { createJob, listJobs, updateJob } from '../../../api/org';
import type { SimpleCrudApi } from '../../../types/admin';

const api: SimpleCrudApi = {
  list: listJobs,
  create: createJob,
  update: updateJob,
};
</script>

<template>
  <CatalogCrudPage
    title="职位管理"
    subtitle="维护职位名称与所属序列，用于招聘与员工档案"
    perm-prefix="org:job"
    name-placeholder="如 高级后端开发工程师"
    :api="api"
  />
</template>
