<script setup lang="ts">
/**
 * 离职办理表单（EMP-F-011）。
 *
 * 前置：员工状态为「在职（ACTIVE）」或「试用（PROBATION）」。
 * 提交后创建 EMP_RESIGN 单，审批通过进入「离职中（RESIGNING）」，
 * 最后工作日到期后由待生效事务落账转「已离职（RESIGNED）」终态。
 *
 * resignType 服务端无枚举约束（仅存入单据 payload），此处按需求 EMP-F-011
 * 描述的「员工/公司两类发起方」给出两个取值，避免自由文本导致统计口径混乱。
 */
import { reactive } from 'vue';
import { message } from 'ant-design-vue';
import { submitResignation } from '../../../api/employee';
import { useLifecycleAction } from './useLifecycleAction';

const resignTypeOptions = [
  { value: 'VOLUNTARY', label: '员工主动离职' },
  { value: 'INVOLUNTARY', label: '公司发起（辞退 / 协商解除）' },
];

const form = reactive({
  empId: undefined as number | undefined,
  lastWorkDate: '',
  resignType: 'VOLUNTARY',
  reason: '',
});

const { submitting, run } = useLifecycleAction();

async function handleSubmit() {
  if (!form.empId) return message.warning('请填写员工 ID');
  if (!form.lastWorkDate) return message.warning('请选择最后工作日');
  if (!form.resignType) return message.warning('请选择离职类型');

  const ok = await run(
    () =>
      submitResignation({
        empId: form.empId,
        lastWorkDate: form.lastWorkDate,
        resignType: form.resignType,
        reason: form.reason || undefined,
      }),
    '离职办理已发起，等待审批',
  );
  if (ok) {
    form.empId = undefined;
    form.reason = '';
  }
}
</script>

<template>
  <a-card :bordered="false">
    <p class="hint">
      仅「在职」或「试用期」员工可发起。审批通过后进入离职中，最后工作日到期自动转已离职并落账。
    </p>
    <a-form layout="vertical" :model="form">
      <a-row :gutter="16">
        <a-col :span="8">
          <a-form-item label="员工 ID" required>
            <a-input-number v-model:value="form.empId" :min="1" style="width: 100%" placeholder="在职/试用员工 ID" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="最后工作日" required>
            <a-date-picker v-model:value="form.lastWorkDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="离职类型" required>
            <a-select v-model:value="form.resignType" :options="resignTypeOptions" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="16">
          <a-form-item label="离职原因">
            <a-textarea v-model:value="form.reason" :rows="3" placeholder="选填，用于离职原因结构化统计" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-button type="primary" danger :loading="submitting" @click="handleSubmit">发起离职办理</a-button>
    </a-form>
  </a-card>
</template>

<style scoped>
.hint {
  margin: 0 0 16px;
  color: var(--color-text-secondary, #666);
  font-size: 13px;
}
</style>
