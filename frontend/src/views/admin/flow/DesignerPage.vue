<script setup lang="ts">
/**
 * BPMN 设计器页（/admin/flow/designer/:modelId）。
 *
 * 流程：GET /flow/models/{id}/xml（text/xml 原文）→ bpmn-js 渲染编辑
 *      → 校验（POST validate-xml，展示 errors）→ 保存（PUT xml，同 key 生成新版本）
 *      → 部署（POST /{id}/deploy）。
 *
 * 关键点：保存后模型 id 变化（新版本），页面即时切换 currentId 并同步路由，
 * 后续保存与部署必须使用新 id，否则会在旧版本上继续叠加或部署旧版本。
 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import BpmnDesigner from '../../../components/admin/flow/BpmnDesigner.vue';
import DesignerToolbar from '../../../components/admin/flow/DesignerToolbar.vue';
import { deployModel, getModelXml, listModelVersions, saveModelXml, validateModelXml } from '../../../api/flow';
import { errorText } from '../../../components/admin/crud-helpers';
import { useUserStore } from '../../../stores/user';
import type { ModelValidateResult } from '../../../types/api-batch9';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('flow:model:write'));

const currentId = ref(Number(route.params.modelId));
const xml = ref<string | null>(null);
const version = ref(0);
const deployState = ref('DRAFT');
const loading = ref(true);
const loadError = ref('');
const dirty = ref(false);
const busy = ref(false);
const validating = ref(false);
const validation = ref<ModelValidateResult | null>(null);

const designerRef = ref<InstanceType<typeof BpmnDesigner> | null>(null);
const canEdit = computed(() => Boolean(xml.value) && !loadError.value);

async function loadMeta(id: number) {
  try {
    const versions = await listModelVersions(id);
    const hit = versions.find((v) => v.id === id) ?? versions[0];
    if (hit) {
      version.value = hit.version;
      deployState.value = hit.deployState;
    }
  } catch {
    // 版本信息仅用于展示，失败不阻断设计
  }
}

async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    xml.value = await getModelXml(currentId.value);
    dirty.value = false;
    validation.value = null;
    await loadMeta(currentId.value);
  } catch (e) {
    loadError.value = errorText(e, '模型 XML 加载失败');
    xml.value = null;
  } finally {
    loading.value = false;
  }
}

async function currentXml(): Promise<string | null> {
  try {
    const value = await designerRef.value?.getXml();
    if (!value) {
      message.error('无法读取画布内容');
      return null;
    }
    return value;
  } catch (e) {
    message.error(errorText(e, '导出 BPMN XML 失败'));
    return null;
  }
}

async function handleValidate() {
  const value = await currentXml();
  if (!value) return;
  validating.value = true;
  try {
    validation.value = await validateModelXml(currentId.value, value);
    if (validation.value.valid) message.success('校验通过');
  } catch (e) {
    message.error(errorText(e, '校验失败'));
  } finally {
    validating.value = false;
  }
}

async function handleSave() {
  const value = await currentXml();
  if (!value) return;
  busy.value = true;
  try {
    const model = await saveModelXml(currentId.value, value);
    currentId.value = model.id;
    version.value = model.version;
    deployState.value = model.deployState;
    dirty.value = false;
    validation.value = null;
    await router.replace({ name: 'AdminFlowDesigner', params: { modelId: String(model.id) } });
    message.success(`已保存为新版本 v${model.version}（模型 #${model.id}），后续操作使用新 ID`);
  } catch (e) {
    message.error(errorText(e, '保存失败'));
  } finally {
    busy.value = false;
  }
}

async function handleDeploy() {
  if (dirty.value) {
    message.warning('存在未保存修改，请先保存再部署');
    return;
  }
  busy.value = true;
  try {
    const res = await deployModel(currentId.value);
    deployState.value = 'DEPLOYED';
    message.success(`部署成功：${res.procDefKey} v${res.version}（部署 ${res.deploymentId}）`);
  } catch (e) {
    message.error(errorText(e, '部署失败'));
  } finally {
    busy.value = false;
  }
}

function goBack() {
  router.push('/admin/flow/models');
}

onMounted(load);
</script>

<template>
  <div class="designer-page">
    <DesignerToolbar
      :model-id="currentId"
      :version="version"
      :deploy-state="deployState"
      :dirty="dirty"
      :busy="busy"
      :validating="validating"
      :can-write="canWrite"
      :validation="validation"
      @back="goBack"
      @validate="handleValidate"
      @save="handleSave"
      @deploy="handleDeploy"
    />

    <div v-if="loading" class="loading"><a-spin /></div>
    <a-result
      v-else-if="loadError"
      status="error"
      title="模型加载失败"
      :sub-title="loadError"
    >
      <template #extra>
        <a-button type="primary" @click="load">重试</a-button>
        <a-button @click="goBack">返回列表</a-button>
      </template>
    </a-result>
    <div v-else-if="xml" class="canvas-wrap">
      <BpmnDesigner
        ref="designerRef"
        :xml="xml"
        @change="dirty = true"
        @error="(msg: string) => (loadError = msg)"
      />
    </div>

    <p v-if="canEdit" class="hint">
      提示：拖拽左侧工具栏元素绘制流程；保存会生成新版本（不自动部署），部署前请先保存。
    </p>
  </div>
</template>

<style scoped>
.designer-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  min-height: 560px;
  background: #fff;
  border-radius: var(--radius-lg, 8px);
  box-shadow: var(--shadow-sm, 0 1px 2px rgba(0, 0, 0, 0.05));
  overflow: hidden;
}
.loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.canvas-wrap {
  flex: 1;
  min-height: 520px;
}
.hint {
  margin: 0;
  padding: 6px 12px;
  font-size: 12px;
  color: var(--color-text-muted);
  border-top: 1px solid var(--color-border, #f0f0f0);
}
</style>
