<script setup lang="ts">
/**
 * 管理端-审计日志：关键操作（登录 / 审批 / 薪酬查看 / 导出 / 权限变更）的只读流水。
 *
 * 日志只追加、不可修改或删除，因此本页不提供新建/编辑/删除。
 * 租户隔离由服务端强制：TraceContext 无 tenantId 时直接 FORBIDDEN，
 * 前端不传租户参数，也无法越权查看其他租户的日志。
 */
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import { listAuditLogs } from '../../../api/platform';
import type { CrudApi, CrudColumn, CrudRow } from '../../../types/admin';
import type { PageResult } from '../../../types/api';

const columns: CrudColumn[] = [
  { key: 'createdAt', title: '时间', width: 170, type: 'datetime' },
  { key: 'action', title: '操作', width: 160 },
  { key: 'resource', title: '资源', width: 160 },
  { key: 'actorId', title: '操作人 ID', width: 120 },
  { key: 'ip', title: '来源 IP', width: 140 },
  { key: 'detail', title: '详情' },
];

const api: CrudApi = {
  list: (page: number, size: number) => listAuditLogs(page, size) as Promise<PageResult<CrudRow>>,
};
</script>

<template>
  <EntityCrudPage
    title="审计日志"
    subtitle="关键操作流水，只追加不可改；按当前租户强制隔离"
    perm-prefix="plt:audit"
    :columns="columns"
    :fields="[]"
    :api="api"
  />
</template>
