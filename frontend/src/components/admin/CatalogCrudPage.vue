<script setup lang="ts">
/**
 * 目录类实体通用 CRUD 页：法人主体 / 岗位 / 职位 / 职级 / 职级带 共用。
 *
 * 这几类实体在契约中都是 CatalogResponse 结构（id / name / code / status，
 * 职级额外带 gradeBandId），差异仅在标题、权限点与是否显示职级带字段。
 * 抽象为一层薄包装，避免五份近乎相同的页面各自漂移。
 */
import SimpleCrudPage from './SimpleCrudPage.vue';
import type { CrudColumn, CrudField, SimpleCrudApi } from '../../types/admin';

const props = withDefaults(
  defineProps<{
    title: string;
    subtitle?: string;
    /** 权限点前缀，如 org:position（按钮按 {prefix}:write 收敛） */
    permPrefix: string;
    /** name 字段的占位提示 */
    namePlaceholder?: string;
    /** code 字段的标签文案（法人叫「统一社会信用代码」，其余叫「编码」） */
    codeLabel?: string;
    /** 是否显示并编辑职级带关联（仅职级使用） */
    withGradeBand?: boolean;
    api: SimpleCrudApi;
  }>(),
  {
    subtitle: '',
    namePlaceholder: '',
    codeLabel: '编码',
    withGradeBand: false,
  },
);

const columns: CrudColumn[] = [
  { key: 'name', title: '名称', width: 200 },
  { key: 'code', title: props.codeLabel, width: 200 },
  ...(props.withGradeBand
    ? ([{ key: 'gradeBandId', title: '所属职级带 ID', width: 140 }] as CrudColumn[])
    : []),
  { key: 'status', title: '状态', width: 100, type: 'tag', valueMap: { '1': '启用', '0': '停用' } },
];

const fields: CrudField[] = [
  {
    key: 'name',
    label: '名称',
    type: 'text',
    required: true,
    placeholder: props.namePlaceholder || undefined,
  },
  { key: 'code', label: props.codeLabel, type: 'text', placeholder: '留空由服务端生成' },
  ...(props.withGradeBand
    ? ([
        {
          key: 'gradeBandId',
          label: '所属职级带 ID',
          type: 'number',
          placeholder: '职级带 ID，可留空',
        },
      ] as CrudField[])
    : []),
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
</script>

<template>
  <SimpleCrudPage
    :title="title"
    :subtitle="subtitle"
    :perm-prefix="permPrefix"
    :columns="columns"
    :fields="fields"
    :api="api"
  />
</template>
