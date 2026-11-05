<script setup lang="ts">
/**
 * 管理端-流程模型：维护 BPMN 流程定义并部署。
 *
 * 流程：新建模型（填 procDefKey + BPMN XML）→ 设计器可视化编辑（/admin/flow/designer/:modelId）
 * → 校验/保存（生成新版本）→ 部署 → 新发起走新版本，在跑实例仍按旧版本继续。
 */
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import EntityCrudPage from '../../../components/admin/EntityCrudPage.vue';
import { createModel, deployModel, listModels } from '../../../api/flow';
import type { CrudApi, CrudColumn, CrudField, CrudRow } from '../../../types/admin';
import type { PageResult } from '../../../types/api';

const router = useRouter();

function openDesigner(record: CrudRow) {
  router.push({ name: 'AdminFlowDesigner', params: { modelId: String(record.id) } });
}

const columns: CrudColumn[] = [
  { key: 'procDefKey', title: '流程定义 Key', width: 220 },
  { key: 'version', title: '版本', width: 90, type: 'number' },
  {
    key: 'deployState',
    title: '部署状态',
    width: 130,
    type: 'tag',
    valueMap: { DRAFT: '草稿', DEPLOYED: '已部署' },
  },
  { key: 'createdAt', title: '创建时间', width: 180, type: 'datetime' },
];

const fields: CrudField[] = [
  { key: 'procDefKey', label: '流程定义 Key', type: 'text', required: true, placeholder: '如 GENERIC_APPROVAL' },
  {
    key: 'bpmnXml',
    label: 'BPMN XML',
    type: 'textarea',
    required: true,
    placeholder: '粘贴 BPMN 2.0 XML 内容',
  },
  {
    key: 'formSchemaJson',
    label: '表单 Schema(JSON)',
    type: 'textarea',
    placeholder: '选填，节点动态表单定义',
  },
];

const api: CrudApi = {
  list: (page: number, size: number) => listModels(page, size) as Promise<PageResult<CrudRow>>,
  create: createModel,
};

async function handleDeploy(record: CrudRow, reload: () => void) {
  try {
    await deployModel(Number(record.id));
    message.success('已部署，新发起的流程将使用该版本');
    reload();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '部署失败');
  }
}
</script>

<template>
  <EntityCrudPage
    title="流程模型"
    subtitle="维护 BPMN 流程定义；部署后新发起走新版本，在跑实例仍按旧版本继续"
    perm-prefix="flow:model"
    :columns="columns"
    :fields="fields"
    :api="api"
  >
    <template #rowActions="{ record, reload }">
      <a-button type="link" size="small" @click="openDesigner(record)">设计</a-button>
      <a-popconfirm
        v-if="record.deployState !== 'DEPLOYED'"
        title="确认部署该流程模型？"
        @confirm="handleDeploy(record, reload)"
      >
        <a-button type="link" size="small">部署</a-button>
      </a-popconfirm>
      <a-tag v-else color="green">已部署</a-tag>
    </template>
  </EntityCrudPage>
</template>
