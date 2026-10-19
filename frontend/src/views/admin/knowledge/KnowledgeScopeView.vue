<script setup lang="ts">

/**
 * 管理端-知识可见范围页：输入员工 ID 拉取档案简报，
 * 用于检索前的知识范围判定（跨域取数，不在知识库域冗余存储员工主数据）。
 */
import EmployeeContextPanel from '../../../components/admin/EmployeeContextPanel.vue';
import { fetchKnowledgeScope } from '../../../api/knowledge';
</script>

<template>
  <EmployeeContextPanel
    title="知识范围"
    subtitle="检索前按员工 ID 拉取档案简报（部门/汇报线/在职状态），用于知识可见性范围判定，避免知识库域冗余存储员工主数据。"
    :fetcher="fetchKnowledgeScope"
  />
</template>
