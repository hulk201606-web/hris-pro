<script setup lang="ts">
/**
 * 流程轨迹弹窗：按实例 ID 拉取节点流水，用时间线展示。
 *
 * 只读展示，不做任何写操作。节点按服务端返回顺序渲染（引擎已按时间排序）。
 */
import { ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { getInstanceTrack } from '../../../api/flow';
import type { FlowTrack } from '../../../api/flow';

const props = defineProps<{ open: boolean; instanceId: string | null }>();
const emit = defineEmits<{ 'update:open': [boolean] }>();

const track = ref<FlowTrack | null>(null);
const loading = ref(false);

function fmt(t?: string): string {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-';
}

function fmtDuration(ms?: number): string {
  if (ms === undefined || ms === null) return '-';
  if (ms < 1000) return `${ms} 毫秒`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)} 秒`;
  return `${(ms / 60000).toFixed(1)} 分钟`;
}

watch(
  () => props.open,
  async (v) => {
    if (!v || !props.instanceId) return;
    loading.value = true;
    track.value = null;
    try {
      track.value = await getInstanceTrack(props.instanceId);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '轨迹加载失败');
    } finally {
      loading.value = false;
    }
  },
);
</script>

<template>
  <a-modal
    :open="open"
    title="流程轨迹"
    :footer="null"
    width="680px"
    @update:open="emit('update:open', $event)"
  >
    <a-spin :spinning="loading">
      <template v-if="track">
        <a-descriptions :column="2" size="small" bordered class="meta">
          <a-descriptions-item label="实例 ID">{{ track.instanceId }}</a-descriptions-item>
          <a-descriptions-item label="状态">{{ track.status }}</a-descriptions-item>
          <a-descriptions-item label="发起人">{{ track.startUserId || '-' }}</a-descriptions-item>
          <a-descriptions-item label="发起时间">{{ fmt(track.startTime) }}</a-descriptions-item>
        </a-descriptions>

        <a-timeline class="timeline">
          <a-timeline-item
            v-for="(n, i) in track.nodes"
            :key="`${n.nodeId}-${i}`"
            :color="n.status === 'COMPLETED' ? 'green' : 'blue'"
          >
            <div class="node-title">{{ n.nodeName }}</div>
            <div class="node-meta">
              处理人：{{ n.assignee || '-' }}　状态：{{ n.status }}　耗时：{{ fmtDuration(n.durationInMillis) }}
            </div>
            <div class="node-meta">{{ fmt(n.startTime) }} → {{ fmt(n.endTime) }}</div>
          </a-timeline-item>
        </a-timeline>

        <a-empty v-if="!track.nodes || !track.nodes.length" description="暂无节点流水" />
      </template>
      <a-empty v-else-if="!loading" description="未取到轨迹" />
    </a-spin>
  </a-modal>
</template>

<style scoped>
.meta {
  margin-bottom: 20px;
}
.timeline {
  max-height: 420px;
  overflow-y: auto;
  padding-top: 4px;
}
.node-title {
  font-weight: 600;
  margin-bottom: 2px;
}
.node-meta {
  color: var(--color-text-secondary, #666);
  font-size: 12px;
}
</style>
