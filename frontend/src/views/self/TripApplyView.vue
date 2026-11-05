<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { createTrip } from '../../api/attendance';
import { errorText } from '../../components/admin/crud-helpers';
import { useUserStore } from '../../stores/user';

/**
 * 出差申请发起页（ATT-F-017）：目的地 + 起止时间 + 事由，提交后由后端自动发起审批流。
 * 补贴天数由后端按自然日跨度派生，前端不传；行程需与已审批请假单互斥（服务端校验）。
 */
const router = useRouter();
const userStore = useUserStore();
const submitting = ref(false);

const form = reactive({
  destination: '',
  startTime: '',
  endTime: '',
  reason: '',
});

async function handleSubmit() {
  if (!form.destination.trim()) {
    message.warning('请填写目的地');
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
    await createTrip({
      empId: userStore.employeeId,
      destination: form.destination.trim(),
      startTime: form.startTime,
      endTime: form.endTime,
      reason: form.reason || undefined,
    });
    message.success('出差申请已提交，等待审批');
    router.push('/self/applications');
  } catch (e) {
    message.error(errorText(e, '提交失败'));
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="trip-apply">
    <section class="head">
      <h2 class="head-title">出差申请</h2>
      <p class="head-sub">提交后自动发起审批流程，补贴天数按行程自然日跨度由系统计算。</p>
    </section>

    <a-card :bordered="false" class="form-card">
      <a-form layout="vertical" :model="form">
        <a-form-item label="目的地" required>
          <a-input v-model:value="form.destination" placeholder="如 上海" />
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
        <a-form-item label="出差事由">
          <a-textarea v-model:value="form.reason" :rows="3" placeholder="选填" />
        </a-form-item>
        <a-button type="primary" :loading="submitting" block @click="handleSubmit">提交申请</a-button>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.trip-apply {
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
