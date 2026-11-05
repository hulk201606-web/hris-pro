<script setup lang="ts">
/**
 * 管理端-岗位管理：岗位序列（jobFamily）与岗位×部门挂靠管理（ORG-F-006 深化）。
 *
 * - 表单含「岗位序列」字段，随 CatalogRequest.jobFamily 提交；
 * - 行操作「挂靠部门」：checkable 部门树多选（check-strictly 保证勾选即精确部门 ID），
 *   打开时 GET /org/positions/{id}/depts 回显，保存 POST 整体替换（空列表即清空，幂等）。
 */
import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import {
  createPosition,
  listDepts,
  listPositionDepts,
  listPositions,
  replacePositionDepts,
  updatePosition,
} from '../../../api/org';
import { errorText } from '../../../components/admin/crud-helpers';
import { useUserStore } from '../../../stores/user';
import type { CrudColumn, CrudField, SimpleCrudApi } from '../../../types/admin';
import type { DeptNode } from '../../../types/api';

const userStore = useUserStore();

const columns: CrudColumn[] = [
  { key: 'name', title: '名称', width: 180 },
  { key: 'code', title: '编码', width: 160 },
  { key: 'jobFamily', title: '岗位序列', width: 130 },
  { key: 'status', title: '状态', width: 100, type: 'tag', valueMap: { '1': '启用', '0': '停用' } },
];

const fields: CrudField[] = [
  { key: 'name', label: '名称', type: 'text', required: true, placeholder: '如 后端开发工程师' },
  { key: 'code', label: '编码', type: 'text', placeholder: '留空由服务端生成' },
  { key: 'jobFamily', label: '岗位序列', type: 'text', placeholder: '如 管理 / 专业 / 销售 / 操作' },
  {
    key: 'status',
    label: '状态',
    type: 'select',
    required: true,
    options: [
      { label: '启用', value: 1 },
      { label: '停用', value: 0 },
    ],
  },
];

const api: SimpleCrudApi = {
  list: listPositions,
  create: createPosition,
  update: updatePosition,
};

/* ---------------------------- 挂靠部门 ---------------------------- */

interface TreeItem {
  key: number;
  title: string;
  children?: TreeItem[];
}

const deptOpen = ref(false);
const deptSaving = ref(false);
const deptLoading = ref(false);
const currentPos = ref<{ id: number; name: string } | null>(null);
const deptTree = ref<DeptNode[]>([]);
const checkedIds = ref<number[]>([]);

const canWrite = computed(() => userStore.hasPerm('org:position:write'));

function toTreeItems(nodes: DeptNode[]): TreeItem[] {
  return nodes.map((n) => ({
    key: n.id,
    title: `${n.name}（${n.code}）`,
    children: n.children?.length ? toTreeItems(n.children) : undefined,
  }));
}

const deptTreeData = computed(() => toTreeItems(deptTree.value));

async function openDeptModal(record: Record<string, unknown>) {
  currentPos.value = { id: Number(record.id), name: String(record.name ?? '') };
  checkedIds.value = [];
  deptOpen.value = true;
  deptLoading.value = true;
  try {
    const [tree, linked] = await Promise.all([listDepts(true), listPositionDepts(currentPos.value.id)]);
    deptTree.value = tree;
    checkedIds.value = linked;
  } catch (e) {
    message.error(errorText(e, '挂靠数据加载失败'));
  } finally {
    deptLoading.value = false;
  }
}

function onDeptCheck(
  _keys: number[] | { checked: number[]; halfChecked: number[] },
  info: { checkedKeys?: number[] | { checked: number[]; halfChecked: number[] } },
) {
  // check-strictly 模式下 checkedKeys 可能是数组或 {checked, halfChecked} 对象
  const ck = info.checkedKeys;
  const list = Array.isArray(ck) ? ck : ck?.checked ?? [];
  checkedIds.value = list.map(Number).filter(Boolean);
}

async function saveDepts() {
  if (!currentPos.value) return;
  deptSaving.value = true;
  try {
    await replacePositionDepts(currentPos.value.id, checkedIds.value);
    message.success('挂靠部门已保存');
    deptOpen.value = false;
  } catch (e) {
    message.error(errorText(e, '保存失败'));
  } finally {
    deptSaving.value = false;
  }
}
</script>

<template>
  <div>
    <SimpleCrudPage
      title="岗位管理"
      subtitle="维护岗位序列（管理 / 专业 / 销售 / 操作）与岗位×部门挂靠，供编制与员工归属使用"
      perm-prefix="org:position"
      :columns="columns"
      :fields="fields"
      :api="api"
    >
      <template #rowActions="{ record }">
        <a-button v-if="canWrite" type="link" size="small" @click="openDeptModal(record)">
          挂靠部门
        </a-button>
      </template>
    </SimpleCrudPage>

    <a-modal
      :open="deptOpen"
      :title="currentPos ? `挂靠部门：${currentPos.name}` : '挂靠部门'"
      :confirm-loading="deptSaving"
      ok-text="保存"
      cancel-text="取消"
      :mask-closable="false"
      @update:open="deptOpen = $event"
      @ok="saveDepts"
    >
      <a-alert
        type="info"
        show-icon
        message="保存为整体替换语义：取消全部勾选即清空挂靠；重复提交幂等。"
        class="tip"
      />
      <a-spin :spinning="deptLoading">
        <a-tree
          v-if="deptTreeData.length"
          :tree-data="deptTreeData"
          checkable
          check-strictly
          :checked-keys="checkedIds"
          default-expand-all
          @check="onDeptCheck"
        />
        <a-empty v-else-if="!deptLoading" description="暂无部门数据" />
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped>
.tip {
  margin-bottom: 12px;
}
</style>
