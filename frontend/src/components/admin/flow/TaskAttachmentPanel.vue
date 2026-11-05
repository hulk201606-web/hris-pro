<script setup lang="ts">
/**
 * 流程任务附件面板（FLW-F-020）：
 * 1) 经 PLT 预签名上传落文件元数据（degraded 时仅登记 fileId，不直传对象存储）；
 * 2) 登记到当前任务节点（POST /flow/tasks/{taskId}/attachments）；
 * 3) 列表按上传时间正序展示。
 */
import { ref, watch } from 'vue';
import axios from 'axios';
import { message } from 'ant-design-vue';
import { UploadOutlined } from '@ant-design/icons-vue';
import { addTaskAttachment, listTaskAttachments } from '../../../api/flow';
import { presignUpload } from '../../../api/platform';
import { errorText } from '../crud-helpers';
import type { FlowAttachment } from '../../../types/api-batch9';

const props = defineProps<{ taskId: string | null }>();

const rows = ref<FlowAttachment[]>([]);
const loading = ref(false);
const uploading = ref(false);

async function load() {
  if (!props.taskId) {
    rows.value = [];
    return;
  }
  loading.value = true;
  try {
    rows.value = await listTaskAttachments(props.taskId);
  } catch (e) {
    message.error(errorText(e, '附件列表加载失败'));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

interface UploadOptions {
  file: File;
  onSuccess?: (body?: unknown) => void;
  onError?: (err: Error) => void;
}

/** 预签名 → 直传 MinIO（未降级时）→ 登记任务附件 */
async function customRequest(options: UploadOptions) {
  const taskId = props.taskId;
  if (!taskId) {
    options.onError?.(new Error('缺少任务 ID'));
    return;
  }
  uploading.value = true;
  try {
    const presign = await presignUpload({
      fileName: options.file.name,
      contentType: options.file.type || undefined,
      sizeBytes: options.file.size,
      module: 'flow-task',
    });
    if (!presign.degraded && presign.uploadUrl) {
      await axios.put(presign.uploadUrl, options.file, {
        headers: { 'Content-Type': options.file.type || 'application/octet-stream' },
      });
    } else {
      message.info('对象存储未就绪，已仅登记文件元数据');
    }
    await addTaskAttachment(taskId, String(presign.fileId), options.file.name);
    message.success('附件已上传');
    options.onSuccess?.();
    await load();
  } catch (e) {
    message.error(errorText(e, '附件上传失败'));
    options.onError?.(e instanceof Error ? e : new Error('上传失败'));
  } finally {
    uploading.value = false;
  }
}

function fmt(v?: string): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—';
}

watch(() => props.taskId, load, { immediate: true });
</script>

<template>
  <div class="attach">
    <div class="attach-head">
      <span class="attach-title">节点附件</span>
      <a-upload
        :show-upload-list="false"
        :custom-request="customRequest"
        :disabled="!taskId || uploading"
      >
        <a-button size="small" :loading="uploading" :disabled="!taskId">
          <template #icon><UploadOutlined /></template>
          上传附件
        </a-button>
      </a-upload>
    </div>
    <a-spin :spinning="loading">
      <a-empty v-if="!rows.length" description="暂无附件" :image-style="{ height: '32px' }" />
      <ul v-else class="attach-list">
        <li v-for="item in rows" :key="item.id">
          <span class="name">{{ item.fileName }}</span>
          <span class="meta">{{ fmt(item.createdAt) }}</span>
        </li>
      </ul>
    </a-spin>
  </div>
</template>

<style scoped>
.attach {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border, #f0f0f0);
}
.attach-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.attach-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.attach-list {
  margin: 0;
  padding: 0;
  list-style: none;
  max-height: 140px;
  overflow: auto;
}
.attach-list li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
}
.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta {
  flex: none;
  color: var(--color-text-muted);
  font-size: 12px;
}
</style>
