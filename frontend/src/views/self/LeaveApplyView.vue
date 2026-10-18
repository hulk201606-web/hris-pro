<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { createLeaveRequest, listLeaveTypes } from '../../api/attendance';
import { errorText } from '../../components/admin/crud-helpers';
import { useUserStore } from '../../stores/user';
import type { AttLeaveType } from '../../types/admin';

/**
 * 请假申请发起页：选择假种 + 起止时间 + 事由，提交后由后端自动发起审批流。
 * 请假天数由后端按 8 小时/天换算，前端不传。
 */
const userStore = useUserStore();
const leaveTypes = ref<AttLeaveType[]>([]);
const typeLoading = ref(false);
const submitting = ref(false);

const form = reactive({
  leaveTypeId: undefined as number | undefined,
  startTime: '',
  endTime: '',
  reason: '',
});

const typeOptions = () =>
  leaveTypes.value.map((t) => ({ value: t.id, label: t.name }));

async function loadTypes() {
  typeLoading.value = true;
  try {
    leaveTypes.value = await listLeaveTypes();
  } catch (e) {
    message.error(errorText(e, '假种加载失败'));
  } finally {
    typeLoading.value = false;
  }
}

async function handleSubmit() {
  if (!form.leaveTypeId) {
    message.warning('请选择假种');
    return;
  }
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
    await createLeaveRequest({
      empId: userStore.employeeId,
      leaveTypeId: form.leaveTypeId,
      startTime: form.startTime,
      endTime: form.endTime,
      reason: form.reason || undefined,
    });
    message.success('请假申请已提交，等待审批');
    form.reason = '';
  } catch (e) {
    message.error(errorText(e, '提交失败'));
  } finally {
    submitting.value = false;
  }
}

onMounted(loadTypes);
</script>

<template>
  <div class="leave-apply">
    <section class="head">
      <h2 class="head-title">请假申请</h2>
      <p class="head-sub">提交后自动发起审批流程，审批通过后自动扣减假期额度。</p>
    </section>

    <a-card :bordered="false" class="form-card">
      <a-form layout="vertical" :model="form">
        <a-form-item label="假种" required>
          <a-select
            v-model:value="form.leaveTypeId"
            :options="typeOptions()"
            :loading="typeLoading"
            placeholder="请选择假种"
            style="width: 100%"
          />
        </a-form-item>
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
        <a-form-item label="请假事由">
          <a-textarea v-model:value="form.reason" :rows="3" placeholder="选填" />
        </a-form-item>
        <a-button type="primary" :loading="submitting" block @click="handleSubmit">提交申请</a-button>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.leave-apply {
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
