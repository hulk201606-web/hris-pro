<script setup lang="ts">
/**
 * 管理端-员工异动办理：入职 / 转正 / 调动 / 离职 四类生命周期单的发起入口。
 *
 * 四类办理各有状态机前置条件（由服务端强制，前端只做提示不预判）：
 *   入职 -> 员工状态必须为「待入职 PENDING」，并通过编制校验（超编返回 51204）
 *   转正 -> 「试用 PROBATION」
 *   调动 -> 「在职 ACTIVE」，目标部门/岗位需通过编制校验
 *   离职 -> 「在职 ACTIVE」或「试用 PROBATION」
 * 因此前端提交失败时直接展示服务端 message，不做二次加工，避免提示与真实规则漂移。
 *
 * 组织目录（部门/岗位/职级/法人）在本页统一加载一次，避免四个表单各自请求。
 */
import { onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import OnboardForm from '../../../components/admin/lifecycle/OnboardForm.vue';
import RegularizeForm from '../../../components/admin/lifecycle/RegularizeForm.vue';
import TransferForm from '../../../components/admin/lifecycle/TransferForm.vue';
import ResignForm from '../../../components/admin/lifecycle/ResignForm.vue';
import { listDepts, listGrades, listLegalEntities, listPositions } from '../../../api/org';
import { errorText } from '../../../components/admin/crud-helpers';

export interface CatalogOption {
  value: number;
  label: string;
}

const deptOptions = ref<CatalogOption[]>([]);
const positionOptions = ref<CatalogOption[]>([]);
const gradeOptions = ref<CatalogOption[]>([]);
const legalEntityOptions = ref<CatalogOption[]>([]);
const loading = ref(false);

async function loadCatalogs() {
  loading.value = true;
  try {
    const [depts, positions, grades, entities] = await Promise.all([
      listDepts(false),
      listPositions(),
      listGrades(),
      listLegalEntities(),
    ]);
    // 部门用「名称（路径）」标注层级，便于在扁平下拉里区分同名部门
    deptOptions.value = depts.map((d) => ({ value: d.id, label: `${d.name}（${d.path}）` }));
    positionOptions.value = positions.map((p) => ({ value: p.id, label: p.name }));
    gradeOptions.value = grades.map((g) => ({ value: g.id, label: g.name }));
    legalEntityOptions.value = entities.map((e) => ({ value: e.id, label: e.name }));
  } catch (e) {
    message.error(errorText(e, '组织目录加载失败，请确认组织模块已初始化'));
  } finally {
    loading.value = false;
  }
}

onMounted(loadCatalogs);
</script>

<template>
  <div class="lifecycle">
    <section class="head">
      <h2 class="head-title">员工异动办理</h2>
      <p class="head-sub">
        四类办理均有状态前置条件，由服务端校验；提交后自动创建生命周期单并触发对应审批流程。
      </p>
    </section>

    <a-spin :spinning="loading">
      <a-tabs>
        <a-tab-pane key="onboard" tab="入职办理">
          <OnboardForm
            :dept-options="deptOptions"
            :position-options="positionOptions"
            :grade-options="gradeOptions"
            :legal-entity-options="legalEntityOptions"
          />
        </a-tab-pane>
        <a-tab-pane key="regularize" tab="转正办理">
          <RegularizeForm />
        </a-tab-pane>
        <a-tab-pane key="transfer" tab="调动办理">
          <TransferForm
            :dept-options="deptOptions"
            :position-options="positionOptions"
            :grade-options="gradeOptions"
          />
        </a-tab-pane>
        <a-tab-pane key="resign" tab="离职办理">
          <ResignForm />
        </a-tab-pane>
      </a-tabs>
    </a-spin>
  </div>
</template>

<style scoped>
.lifecycle {
  padding: 4px;
}
.head {
  margin-bottom: 16px;
}
.head-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}
.head-sub {
  margin: 0;
  color: var(--color-text-secondary, #666);
  font-size: 13px;
}
</style>
