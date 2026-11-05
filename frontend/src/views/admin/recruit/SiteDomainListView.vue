<script setup lang="ts">
/**
 * 管理端-站点域名管理（批 7 #13）：官网租户域名映射 CRUD + 行内启用状态开关，
 * 走 EntityCrudPage 组件 + /rcu/site-domains 接口。
 * 契约（SiteDomainController）：domain 全局唯一（小写、不含协议与端口），
 * status 取值 ENABLED/DISABLED；权限点 rcu:site:read / rcu:site:write。
 */
import { computed, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import {
  createSiteDomain,
  deleteSiteDomain,
  listSiteDomains,
  updateSiteDomain,
} from '../../../api/recruit';
import { useUserStore } from '../../../stores/user';
import type { CrudApi, CrudColumn, CrudField, CrudRow } from '../../../types/admin';

const userStore = useUserStore();
// 启用状态开关走 rcu:site:write（更新接口）
const canWrite = computed(() => userStore.hasPerm('rcu:site:write'));

const columns: CrudColumn[] = [
  { key: 'domain', title: '域名', width: 240 },
  { key: 'tenantId', title: '租户 ID', width: 100 },
  { key: 'status', title: '状态', width: 100, type: 'tag' },
  { key: 'updatedAt', title: '更新时间', width: 170, type: 'datetime' },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  {
    key: 'domain',
    label: '域名',
    type: 'text',
    required: true,
    placeholder: '小写域名，不含协议与端口，如 jobs.example.com',
  },
  {
    key: 'status',
    label: '状态',
    type: 'select',
    options: [
      { label: '启用', value: 'ENABLED' },
      { label: '停用', value: 'DISABLED' },
    ],
  },
];

const api: CrudApi = {
  list: listSiteDomains,
  create: createSiteDomain,
  update: updateSiteDomain,
  remove: deleteSiteDomain,
};

const toggling = reactive<Record<number, boolean>>({});

/** 行内启用开关：仅改 status，未传字段不更新；成功后刷新列表 */
async function onToggleStatus(record: CrudRow, enabled: boolean, reload: () => Promise<void>) {
  const id = Number(record.id);
  toggling[id] = true;
  try {
    await updateSiteDomain(id, { status: enabled ? 'ENABLED' : 'DISABLED' });
    message.success(enabled ? '已启用' : '已停用');
    await reload();
  } catch (e) {
    message.error(errorText(e, '状态更新失败'));
  } finally {
    toggling[id] = false;
  }
}
</script>

<template>
  <EntityCrudPage
    title="站点域名"
    subtitle="维护官网招聘站点的租户域名映射，域名全局唯一且不含协议与端口"
    perm-prefix="rcu:site"
    :columns="columns"
    :fields="fields"
    :api="api"
  >
    <template #rowActions="{ record, reload }">
      <a-switch
        v-if="canWrite"
        :checked="record.status === 'ENABLED'"
        checked-children="启用"
        un-checked-children="停用"
        size="small"
        :loading="toggling[Number(record.id)]"
        @change="(checked: any) => onToggleStatus(record, Boolean(checked), reload)"
      />
    </template>
  </EntityCrudPage>
</template>
