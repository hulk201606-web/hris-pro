<script setup lang="ts">
/**
 * 管理端-职级体系：职级（如 P4/P5/M1）与所属职级带的关联。
 * 职级带的宽带薪档由薪酬模块（CMP）引用，用于定薪落档校验。
 */
import CatalogCrudPage from '../../../components/admin/CatalogCrudPage.vue';
import { createGrade, listGrades, updateGrade } from '../../../api/org';
import type { SimpleCrudApi } from '../../../types/admin';

const api: SimpleCrudApi = {
  list: listGrades,
  create: createGrade,
  update: updateGrade,
};
</script>

<template>
  <CatalogCrudPage
    title="职级体系"
    subtitle="维护职级并关联职级带；职级带供薪酬模块做定薪落档校验"
    perm-prefix="org:grade"
    name-placeholder="如 P6"
    with-grade-band
    :api="api"
  />
</template>
