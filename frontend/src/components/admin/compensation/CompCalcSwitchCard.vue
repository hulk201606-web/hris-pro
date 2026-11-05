<script setup lang="ts">
/**
 * 计薪开关卡片（CMP-F-011）：租户级参数 hris.cmp.calc.pay-termination-day。
 * 读取顺序与后端 CmpCalcConfigService 一致：sys_tenant_config 优先，缺省按 true（离职当日计薪）。
 * 复用系统配置页同一组 /plt/configs 接口（configKey 定位 + PUT upsert）。
 */
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { listConfigs, saveConfig } from '../../../api/platform';
import { errorText } from '../crud-helpers';
import { useUserStore } from '../../../stores/user';

const CONFIG_KEY = 'hris.cmp.calc.pay-termination-day';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('plt:config:write'));

const loading = ref(false);
const saving = ref(false);
const enabled = ref(true);
const configured = ref(false);

function parse(raw: string | undefined): boolean {
  if (!raw) return true;
  const v = raw.trim().toLowerCase();
  if (['true', '1', 'yes', 'on'].includes(v)) return true;
  if (['false', '0', 'no', 'off'].includes(v)) return false;
  return true;
}

async function load() {
  loading.value = true;
  try {
    const rows = await listConfigs();
    const hit = rows.find((r) => r.configKey === CONFIG_KEY);
    configured.value = Boolean(hit);
    enabled.value = parse(hit?.configValue);
  } catch (e) {
    message.error(errorText(e, '计薪开关读取失败'));
  } finally {
    loading.value = false;
  }
}

async function handleChange(next: boolean) {
  saving.value = true;
  try {
    await saveConfig(CONFIG_KEY, String(next));
    enabled.value = next;
    configured.value = true;
    message.success(next ? '已开启：离职当日计薪' : '已关闭：离职当日不计薪');
  } catch (e) {
    // 失败回滚开关展示，避免与服务端不一致
    enabled.value = !next;
    message.error(errorText(e, '计薪开关保存失败'));
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <a-card :bordered="false" class="calc-switch" :loading="loading">
    <div class="row">
      <div>
        <div class="title">离职当日计薪</div>
        <div class="desc">
          配置键 {{ CONFIG_KEY }}；{{ configured ? '当前为租户显式配置' : '未显式配置，按部署默认（开启）' }}
        </div>
      </div>
      <a-switch
        :checked="enabled"
        :loading="saving"
        :disabled="!canWrite"
        checked-children="计薪"
        un-checked-children="不计"
        @change="(v: unknown) => handleChange(Boolean(v))"
      />
    </div>
    <div v-if="!canWrite" class="tip">需要权限 plt:config:write 才能修改</div>
  </a-card>
</template>

<style scoped>
.calc-switch {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}
.title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
