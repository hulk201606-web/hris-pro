<script setup lang="ts">
/**
 * 管理端-职级带：职级区间定义（如 P4-P9 为技术带、M1-M5 为管理带）。
 * 职级带是最粗粒度的薪酬带宽载体，薪酬带宽薪档挂在其下。
 */
import CatalogCrudPage from '../../../components/admin/CatalogCrudPage.vue';
import { createGradeBand, listGradeBands, updateGradeBand } from '../../../api/org';
import type { SimpleCrudApi } from '../../../types/admin';

const api: SimpleCrudApi = {
  list: listGradeBands,
  create: createGradeBand,
  update: updateGradeBand,
};
</script>

<template>
  <CatalogCrudPage
    title="职级带"
    subtitle="定义职级区间（如 P4-P9 技术带 / M1-M5 管理带），承载薪酬带宽"
    perm-prefix="org:grade-band"
    name-placeholder="如 技术带"
    :api="api"
  />
</template>
