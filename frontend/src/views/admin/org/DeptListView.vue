<script setup lang="ts">
/**
 * 管理端-部门管理：部门树维护（名称 / 编码 / 上级部门 / 负责人 / 生效日期）。
 *
 * 提供两个视图：
 *  - 平铺列表：SimpleCrudPage 维护字段（新建/编辑走表单，层级由 parentId + path 体现）；
 *  - 拖拽排序：a-tree draggable，drop 时调 POST /org/depts/{id}/move（ORG-F-002 深化）。
 *    前端预校验不可拖到自身/自身子孙下；后端拒绝（如深度超限）时 message.error
 *    并重新拉取树回滚 UI（树由 treeData 受控，重载即还原）。
 */
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import SimpleCrudPage from '../../../components/admin/SimpleCrudPage.vue';
import { createDept, listDepts, moveDept, updateDept } from '../../../api/org';
import { errorText } from '../../../components/admin/crud-helpers';
import type { CrudColumn, CrudField, SimpleCrudApi } from '../../../types/admin';
import type { DeptNode } from '../../../types/api';

const columns: CrudColumn[] = [
  { key: 'path', title: '层级路径', width: 130 },
  { key: 'name', title: '部门名称', width: 180 },
  { key: 'code', title: '部门编码', width: 140 },
  { key: 'parentId', title: '上级部门 ID', width: 120 },
  { key: 'managerEmpId', title: '负责人 ID', width: 120 },
  { key: 'status', title: '状态', width: 90, type: 'tag', valueMap: { '1': '启用', '0': '停用' } },
  { key: 'effectiveFrom', title: '生效日期', width: 120, type: 'date' },
];

const fields: CrudField[] = [
  { key: 'name', label: '部门名称', type: 'text', required: true },
  { key: 'code', label: '部门编码', type: 'text', placeholder: '留空由服务端自动分配' },
  { key: 'parentId', label: '上级部门 ID', type: 'number', placeholder: '留空表示根节点' },
  { key: 'managerEmpId', label: '部门负责人 ID', type: 'number', placeholder: '员工 ID，可留空' },
  { key: 'effectiveFrom', label: '生效日期', type: 'date', placeholder: '留空为立即生效' },
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
  list: () => listDepts(false),
  create: createDept,
  update: updateDept,
};

/* ---------------------------- 拖拽排序视图 ---------------------------- */

interface TreeItem {
  key: number;
  title: string;
  children?: TreeItem[];
}

const activeView = ref<'list' | 'tree'>('list');
const treeNodes = ref<DeptNode[]>([]);
const treeLoading = ref(false);
const moving = ref(false);

function toTreeItems(nodes: DeptNode[]): TreeItem[] {
  return nodes.map((n) => ({
    key: n.id,
    title: `${n.name}（${n.code}）`,
    children: n.children?.length ? toTreeItems(n.children) : undefined,
  }));
}

const treeData = computed(() => toTreeItems(treeNodes.value));

async function loadTree() {
  treeLoading.value = true;
  try {
    treeNodes.value = await listDepts(true);
  } catch (e) {
    message.error(errorText(e, '部门树加载失败'));
    treeNodes.value = [];
  } finally {
    treeLoading.value = false;
  }
}

/** 收集以 root 为根的子树内全部节点 key（含 root 自身） */
function collectKeys(nodes: DeptNode[], out: Set<number>): void {
  for (const n of nodes) {
    out.add(n.id);
    if (n.children?.length) collectKeys(n.children, out);
  }
}

/** 在树中查找 key 对应节点（返回节点与所在兄弟数组） */
function locate(nodes: DeptNode[], key: number): { node: DeptNode; siblings: DeptNode[] } | null {
  for (const n of nodes) {
    if (n.id === key) return { node: n, siblings: nodes };
    if (n.children?.length) {
      const hit = locate(n.children, key);
      if (hit) return hit;
    }
  }
  return null;
}

interface DropInfo {
  node: { key: number; pos: string };
  dragNode: { key: number };
  dropToGap: boolean;
  dropPosition: number;
}

async function handleDrop(info: DropInfo) {
  const dragKey = Number(info.dragNode.key);
  const targetKey = Number(info.node.key);
  const dragHit = locate(treeNodes.value, dragKey);
  if (!dragHit) return;

  // 预校验：目标父节点不可是被拖节点自身或其子孙（自祖循环）
  const subtree = new Set<number>();
  collectKeys([dragHit.node], subtree);
  let newParentId: number | null;
  let sortOrder: number;
  if (!info.dropToGap) {
    if (subtree.has(targetKey)) {
      message.warning('不能移动到自身或自身子孙部门');
      return;
    }
    newParentId = targetKey;
    const targetHit = locate(treeNodes.value, targetKey);
    sortOrder = targetHit?.node.children?.length ?? 0;
  } else {
    const targetHit = locate(treeNodes.value, targetKey);
    if (!targetHit) return;
    newParentId = targetHit.node.parentId || null;
    const idx = targetHit.siblings.findIndex((s) => s.id === targetKey);
    // antd 约定：dropPosition === -1 表示插到目标之前（顶部），其余为插到目标之后
    sortOrder = info.dropPosition === -1 ? Math.max(idx, 0) : idx + 1;
    if (newParentId !== null && subtree.has(newParentId)) {
      message.warning('不能移动到自身或自身子孙部门下');
      return;
    }
  }

  moving.value = true;
  try {
    await moveDept(dragKey, newParentId, sortOrder);
    message.success('部门已移动');
  } catch (e) {
    message.error(errorText(e, '移动失败，已回滚'));
  } finally {
    moving.value = false;
    loadTree();
  }
}

function switchView(key: string | number) {
  activeView.value = key as 'list' | 'tree';
  if (activeView.value === 'tree') loadTree();
}
</script>

<template>
  <div class="dept-view">
    <a-tabs :active-key="activeView" @change="switchView">
      <a-tab-pane key="list" tab="平铺列表" />
      <a-tab-pane key="tree" tab="拖拽排序" />
    </a-tabs>

    <SimpleCrudPage
      v-if="activeView === 'list'"
      title="部门管理"
      subtitle="维护部门树（深度不超过 8 层）；层级由物化路径体现，新建子部门需指定上级部门 ID"
      perm-prefix="org:dept"
      :columns="columns"
      :fields="fields"
      :api="api"
    />

    <template v-else>
      <section class="head">
        <div>
          <h2 class="head-title">部门树拖拽排序</h2>
          <p class="head-sub">拖拽节点调整层级与顺序；不可拖到自身或子孙部门下，服务端校验失败会自动回滚。</p>
        </div>
      </section>
      <a-card :bordered="false" class="table-card">
        <a-spin :spinning="treeLoading || moving">
          <a-tree
            :tree-data="treeData"
            draggable
            block-node
            default-expand-all
            @drop="handleDrop"
          />
        </a-spin>
      </a-card>
    </template>
  </div>
</template>

<style scoped>
.dept-view {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.head-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: var(--ls-heading);
  color: var(--color-text-primary);
}
.head-sub {
  margin: var(--space-2) 0 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}
.table-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
</style>
