<script setup lang="ts">

/**
 * 管理端-考勤组列表：简单 CRUD（名称 / 编码 / 组长）+ 批 12#27 组策略字段。
 * 批 12#27 新增：workMode（FIXED/FLEXIBLE）、timezone（IANA）、timeWindowMinutes（弹性区间）、
 * locationId（绑定围栏）、requireLocation（是否强制定位）。围栏下拉取自 GET /att/locations。
 * 行操作「成员管理」（批 6 #10）：成员抽屉保存走全量覆盖 POST /att/groups/{id}/members。
 */
import { computed, onMounted, ref } from 'vue';
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import GroupMemberDrawer from '../../../components/admin/GroupMemberDrawer.vue';
import { createGroup, listGroups, updateGroup } from '../../../api/attendance';
import { listLocations } from '../../../api/attendance-fence';
import { useUserStore } from '../../../stores/user';
import type { CrudColumn, CrudField, CrudRow, SimpleCrudApi } from '../../../types/admin';
import type { AttLocation } from '../../../types/api';

const userStore = useUserStore();
// 成员保存走 att:group:write（与考勤组编辑同权限码）
const canWrite = computed(() => userStore.hasPerm('att:group:write'));

// 成员管理抽屉：记录点击行的考勤组
const memberGroup = ref<{ id: number; name: string } | null>(null);

// 围栏下拉：未绑定时可为空（后端视为不做地理校验）
const locations = ref<AttLocation[]>([]);

function openMemberDrawer(record: CrudRow) {
  memberGroup.value = { id: Number(record.id), name: String(record.name ?? '') };
}

const columns = computed<CrudColumn[]>(() => {
  const locationMap: Record<string, string> = { '': '未绑定' };
  locations.value.forEach((l) => {
    locationMap[String(l.id)] = l.name;
  });
  return [
    { key: 'name', title: '班组名称', width: 170 },
    { key: 'code', title: '班组编码', width: 140 },
    { key: 'leaderEmpId', title: '组长员工 ID', width: 120 },
    {
      key: 'workMode',
      title: '工时模式',
      width: 130,
      type: 'tag',
      valueMap: { FIXED: '固定工时', FLEXIBLE: '弹性工时' } as Record<string, string>,
    },
    { key: 'timezone', title: '时区', width: 150 },
    { key: 'timeWindowMinutes', title: '弹性区间(分)', width: 120 },
    { key: 'locationId', title: '绑定围栏', width: 150, valueMap: locationMap },
    {
      key: 'requireLocation',
      title: '强制定位',
      width: 110,
      type: 'tag',
      valueMap: { true: '强制', false: '不强制' } as Record<string, string>,
    },
    { key: 'createdAt', title: '创建时间', width: 170, type: 'datetime' },
  ];
});

const fields = computed<CrudField[]>(() => [
  { key: 'name', label: '班组名称', type: 'text', required: true, placeholder: '如 研发中心' },
  { key: 'code', label: '班组编码', type: 'text', placeholder: '留空由服务端生成' },
  { key: 'leaderEmpId', label: '组长员工 ID', type: 'number', min: 1 },
  {
    key: 'workMode',
    label: '工时模式',
    type: 'select',
    options: [
      { label: '固定工时（FIXED）', value: 'FIXED' },
      { label: '弹性工时（FLEXIBLE）', value: 'FLEXIBLE' },
    ],
  },
  { key: 'timezone', label: '时区', type: 'text', placeholder: 'IANA 时区，如 Asia/Shanghai' },
  {
    key: 'timeWindowMinutes',
    label: '弹性区间（分钟）',
    type: 'number',
    min: 0,
    placeholder: '弹性工时下的前后浮动分钟数',
  },
  {
    key: 'locationId',
    label: '绑定围栏',
    type: 'select',
    options: locations.value.map((l) => ({ label: `${l.name}（${l.radiusM ?? 200}m）`, value: l.id })),
  },
  { key: 'requireLocation', label: '强制定位打卡', type: 'switch' },
]);

const api: SimpleCrudApi = {
  list: listGroups,
  create: createGroup,
  update: updateGroup,
};

onMounted(async () => {
  try {
    locations.value = await listLocations();
  } catch {
    locations.value = [];
  }
});
</script>

<template>
  <SimpleCrudPage
    title="考勤组"
    subtitle="维护考勤班组、工时模式与围栏绑定，班组用于绑定考勤政策与排班规则"
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
