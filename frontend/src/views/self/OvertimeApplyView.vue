<script setup lang="ts">
import { reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { createOvertimeRequest } from '../../api/attendance';
import { errorText } from '../../components/admin/crud-helpers';
import { useUserStore } from '../../stores/user';

/** 加班申请发起页：起止时间 + 事由 + 模式，提交后自动发起审批流。 */
const userStore = useUserStore();
const submitting = ref(false);

const form = reactive({
  startTime: '',
  endTime: '',
  mode: 'BEFORE' as string,
  reason: '',
});

const modeOptions = [
  { value: 'BEFORE', label: '事前审批' },
  { value: 'AFTER', label: '事后补录' },
];

async function handleSubmit() {
  if (!form.startTime || !form.endTime) {
    message.warning('请选择起止时间');
    return;
  }
  if (form.endTime <= form.startTime) {
    message.warning('结束时间必须晚于开始时间');
    return;
  }
  submitting.value = true;
  try {
    await createOvertimeRequest({
      empId: userStore.employeeId,
      startTime: form.startTime,
      endTime: form.endTime,
      mode: form.mode,
      reason: form.reason || undefined,
    });
    message.success('加班申请已提交，等待审批');
    form.reason = '';
  } catch (e) {
    message.error(errorText(e, '提交失败'));
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="ot-apply">
    <section class="head">
      <h2 class="head-title">加班申请</h2>
      <p class="head-sub">加班时长由起止时间换算，费率按工作日/休息日/节假日自动判定。</p>
    </section>

    <a-card :bordered="false" class="form-card">
      <a-form layout="vertical" :model="form">
        <a-form-item label="开始时间" required>
          <a-date-picker
            v-model:value="form.startTime"
            show-time
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="请选择开始时间"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="结束时间" required>
          <a-date-picker
            v-model:value="form.endTime"
            show-time
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="请选择结束时间"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="模式">
          <a-select v-model:value="form.mode" :options="modeOptions" style="width: 100%" />
        </a-form-item>
        <a-form-item label="加班事由">
          <a-textarea v-model:value="form.reason" :rows="3" placeholder="选填" />
        </a-form-item>
        <a-button type="primary" :loading="submitting" block @click="handleSubmit">提交申请</a-button>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.ot-apply {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  max-width: 520px;
}

.head-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: var(--ls-heading);
  color: var(--color-text-primary);
}

.head-sub {
  margin: var(--space-2) 0 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.form-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
</style>
