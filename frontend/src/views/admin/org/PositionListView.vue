<script setup lang="ts">
/**
 * 管理端-岗位管理：岗位序列与岗位台账（管理/专业/销售/操作）。
 * 岗位是编制与员工归属的维度之一。
 */
import CatalogCrudPage from '../../../components/admin/CatalogCrudPage.vue';
import { createPosition, deletePosition, listPositions, updatePosition } from '../../../api/org';
import type { SimpleCrudApi } from '../../../types/admin';

const api: SimpleCrudApi = {
  list: listPositions,
  create: createPosition,
  update: updatePosition,
};
</script>

<template>
  <CatalogCrudPage
    title="岗位管理"
    subtitle="维护岗位序列（管理 / 专业 / 销售 / 操作），供编制与员工归属使用"
    perm-prefix="org:position"
    name-placeholder="如 后端开发工程师"
    :api="api"
  />
</template>
