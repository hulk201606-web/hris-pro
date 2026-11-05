<script setup lang="ts">
/**
 * BPMN 画布容器：封装 bpmn-js Modeler 的生命周期与 XML 导入/导出。
 * 仅提供图形编辑能力（无属性面板扩展），属性修改通过右侧/外部表单或原始 XML 完成。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';

const props = defineProps<{ xml: string }>();
const emit = defineEmits<{ error: [string]; change: [] }>();

const container = ref<HTMLDivElement | null>(null);
let modeler: BpmnModeler | null = null;

onMounted(async () => {
  if (!container.value) return;
  modeler = new BpmnModeler({ container: container.value });
  modeler.on('commandStack.changed', () => emit('change'));
  try {
    await modeler.importXML(props.xml);
    const canvas = modeler.get('canvas') as { zoom: (v: string | number) => void };
    canvas.zoom('fit-viewport');
  } catch (e) {
    emit('error', e instanceof Error ? e.message : 'BPMN 图形渲染失败');
  }
});

onBeforeUnmount(() => {
  modeler?.destroy();
  modeler = null;
});

/** 导出当前画布 XML（format=true 便于服务端 diff 与人工审阅） */
async function getXml(): Promise<string> {
  if (!modeler) throw new Error('设计器尚未就绪');
  const result = await modeler.saveXML({ format: true });
  if (!result.xml) throw new Error('未能导出 BPMN XML');
  return result.xml;
}

defineExpose({ getXml });
</script>

<template>
  <div ref="container" class="canvas" />
</template>

<style scoped>
.canvas {
  width: 100%;
  height: 100%;
  min-height: 520px;
  background: #fff;
}
</style>
