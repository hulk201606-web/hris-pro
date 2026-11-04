<script setup lang="ts">
/**
 * 管理端-法人主体：维护租户下的多法人主体（名称 / 统一社会信用代码 / 状态）。
 * 组织架构的归属基准，部门与编制均挂在法人下。
 */
import CatalogCrudPage from '../../../components/admin/CatalogCrudPage.vue';
import { createLegalEntity, listLegalEntities, updateLegalEntity } from '../../../api/org';
import type { SimpleCrudApi } from '../../../types/admin';

const api: SimpleCrudApi = {
  list: listLegalEntities,
  create: createLegalEntity,
  update: updateLegalEntity,
};
</script>

<template>
  <CatalogCrudPage
    title="法人主体"
    subtitle="维护租户下的法律主体，是部门与编制的归属基准"
    perm-prefix="org:legal-entity"
    code-label="统一社会信用代码"
    name-placeholder="如 智云科技有限公司"
    :api="api"
  />
</template>
