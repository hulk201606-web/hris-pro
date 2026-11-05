<script setup lang="ts">

/**
 * 管理端-考勤组列表：简单 CRUD（考勤组名称 / 编码 / 组长），
 * 走 SimpleCrudPage 组件 + /att/groups 接口。
 * 行操作「成员管理」（批 6 #10）：打开成员抽屉，员工多选保存走全量覆盖
 * POST /att/groups/{id}/members。
 */
import { computed, ref } from 'vue';
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import GroupMemberDrawer from '../../../components/admin/GroupMemberDrawer.vue';
import { createGroup, listGroups, updateGroup } from '../../../api/attendance';
import { useUserStore } from '../../../stores/user';
import type { CrudColumn, CrudField, CrudRow, SimpleCrudApi } from '../../../types/admin';

const userStore = useUserStore();
// 成员保存走 att:group:write（与考勤组编辑同权限码）
const canWrite = computed(() => userStore.hasPerm('att:group:write'));

// 成员管理抽屉：记录点击行的考勤组
const memberGroup = ref<{ id: number; name: string } | null>(null);

function openMemberDrawer(record: CrudRow) {
  memberGroup.value = { id: Number(record.id), name: String(record.name ?? '') };
}

const columns: CrudColumn[] = [
  { key: 'name', title: '班组名称', width: 180 },
  { key: 'code', title: '班组编码', width: 160 },
  { key: 'leaderEmpId', title: '组长员工 ID', width: 130 },
  { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'name', label: '班组名称', type: 'text', required: true, placeholder: '如 研发中心' },
  { key: 'code', label: '班组编码', type: 'text', placeholder: '留空由服务端生成' },
  { key: 'leaderEmpId', label: '组长员工 ID', type: 'number', min: 1 },
];

const api: SimpleCrudApi = {
  list: listGroups,
  create: createGroup,
  update: updateGroup,
};
</script>

<template>
  <SimpleCrudPage
    title="考勤组"
    subtitle="维护考勤班组与组长，班组用于绑定考勤政策与排班规则"
    perm-prefix="att:group"
    :columns="columns"
    :fields="fields"
    :api="api"
  >
    <template #rowActions="{ record }">
      <a-button type="link" size="small" @click="openMemberDrawer(record)">
        成员管理
      </a-button>
    </template>
  </SimpleCrudPage>

  <GroupMemberDrawer
    v-if="memberGroup"
    :group-id="memberGroup.id"
    :group-name="memberGroup.name"
    :can-write="canWrite"
    @close="memberGroup = null"
  />
</template>
