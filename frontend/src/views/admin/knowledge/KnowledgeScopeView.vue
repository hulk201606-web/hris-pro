<script setup lang="ts">
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
