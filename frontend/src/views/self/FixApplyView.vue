<script setup lang="ts">
import { reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { createFixRequest } from '../../api/attendance';
import { errorText } from '../../components/admin/crud-helpers';
import { useUserStore } from '../../stores/user';

/** 补卡申请发起页：补卡日期 + 补卡时间 + 类型（上班/下班），受月度次数限制。 */
const userStore = useUserStore();
const submitting = ref(false);

const form = reactive({
  workDate: '',
  fixTime: '',
  type: 'IN' as string,
  reason: '',
});

const typeOptions = [
  { value: 'IN', label: '上班卡' },
  { value: 'OUT', label: '下班卡' },
];

async function handleSubmit() {
  if (!form.workDate) {
    message.warning('请选择补卡日期');
    return;
  }
  if (!form.fixTime) {
    message.warning('请选择补卡时间');
    return;
  }
  submitting.value = true;
  try {
    await createFixRequest({
      empId: userStore.employeeId,
      workDate: form.workDate,
      fixTime: form.fixTime,
      type: form.type,
      reason: form.reason || undefined,
    });
    message.success('补卡申请已提交，等待审批');
    form.reason = '';
  } catch (e) {
    message.error(errorText(e, '提交失败'));
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="fix-apply">
    <section class="head">
      <h2 class="head-title">补卡申请</h2>
      <p class="head-sub">漏打卡补录，同一工作日重复提交受月度次数上限约束。</p>
    </section>

    <a-card :bordered="false" class="form-card">
      <a-form layout="vertical" :model="form">
        <a-form-item label="补卡日期" required>
          <a-date-picker
            v-model:value="form.workDate"
            value-format="YYYY-MM-DD"
            placeholder="请选择补卡日期"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="补卡时间" required>
          <a-date-picker
            v-model:value="form.fixTime"
            show-time
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="请选择补卡时间"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="补卡类型" required>
          <a-select v-model:value="form.type" :options="typeOptions" style="width: 100%" />
        </a-form-item>
        <a-form-item label="补卡事由">
          <a-textarea v-model:value="form.reason" :rows="3" placeholder="选填" />
        </a-form-item>
        <a-button type="primary" :loading="submitting" block @click="handleSubmit">提交申请</a-button>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.fix-apply {
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
