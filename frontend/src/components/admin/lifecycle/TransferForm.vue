<script setup lang="ts">
/**
 * 调动办理表单（EMP-F-009）。
 *
 * 前置：员工状态必须为「在职（ACTIVE）」。提交后创建 EMP_TRANSFER 单，
 * 服务端会对**目标部门/岗位**做编制校验（不足抛 51204）。
 * 生效日之前不改变员工主状态与归属，到生效日才落账。
 */
import { reactive } from 'vue';
import { message } from 'ant-design-vue';
import { submitTransfer } from '../../../api/employee';
import { useLifecycleAction } from './useLifecycleAction';

const props = defineProps<{
  deptOptions: { value: number; label: string }[];
  positionOptions: { value: number; label: string }[];
  gradeOptions: { value: number; label: string }[];
}>();

const form = reactive({
  empId: undefined as number | undefined,
  effectDate: '',
  targetDeptId: undefined as number | undefined,
  targetPositionId: undefined as number | undefined,
  targetGradeId: undefined as number | undefined,
  targetSupervisorEmpId: undefined as number | undefined,
  reason: '',
});

const { submitting, run } = useLifecycleAction();

async function handleSubmit() {
  if (!form.empId) return message.warning('请填写员工 ID');
  if (!form.effectDate) return message.warning('请选择生效日期');
  if (!form.targetDeptId) return message.warning('请选择目标部门');

  const ok = await run(
    () =>
      submitTransfer({
        empId: form.empId,
        effectDate: form.effectDate,
        targetDeptId: form.targetDeptId,
        targetPositionId: form.targetPositionId,
        targetGradeId: form.targetGradeId,
        targetSupervisorEmpId: form.targetSupervisorEmpId,
        reason: form.reason || undefined,
      }),
    '调动办理已发起，等待审批',
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
      仅「在职」员工可发起。目标部门与岗位会做编制校验，超编将被拒绝；生效日之前员工归属不变。
    </p>
    <a-form layout="vertical" :model="form">
      <a-row :gutter="16">
        <a-col :span="8">
          <a-form-item label="员工 ID" required>
            <a-input-number v-model:value="form.empId" :min="1" style="width: 100%" placeholder="在职员工 ID" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="生效日期" required>
            <a-date-picker v-model:value="form.effectDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="目标部门" required>
            <a-select
              v-model:value="form.targetDeptId"
              :options="props.deptOptions"
              placeholder="请选择目标部门"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="目标岗位">
            <a-select
              v-model:value="form.targetPositionId"
              :options="props.positionOptions"
              allow-clear
              placeholder="选填"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="目标职级">
            <a-select
              v-model:value="form.targetGradeId"
              :options="props.gradeOptions"
              allow-clear
              placeholder="选填"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="目标直属上级 ID">
            <a-input-number
              v-model:value="form.targetSupervisorEmpId"
              :min="1"
              style="width: 100%"
              placeholder="选填"
            />
          </a-form-item>
        </a-col>
        <a-col :span="16">
          <a-form-item label="调动原因">
            <a-textarea v-model:value="form.reason" :rows="2" placeholder="选填" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-button type="primary" :loading="submitting" @click="handleSubmit">发起调动办理</a-button>
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
