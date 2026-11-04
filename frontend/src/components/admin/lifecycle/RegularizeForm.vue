<script setup lang="ts">
/**
 * 转正办理表单（EMP-F-008）。
 *
 * 前置：员工状态必须为「试用（PROBATION）」。提交后创建 EMP_REGULAR 单，
 * 转正生效时状态机将 PROBATION -> ACTIVE 并落状态流水。
 * 支持提前转正与延期转正——由 regularDate 与试用期结束日的相对关系决定，
 * 服务端不做日期约束，这里也不拦截。
 */
import { reactive } from 'vue';
import { message } from 'ant-design-vue';
import { submitRegularization } from '../../../api/employee';
import { useLifecycleAction } from './useLifecycleAction';

const form = reactive({
  empId: undefined as number | undefined,
  regularDate: '',
  comment: '',
  reason: '',
});

const { submitting, run } = useLifecycleAction();

async function handleSubmit() {
  if (!form.empId) return message.warning('请填写员工 ID');
  if (!form.regularDate) return message.warning('请选择转正日期');

  const ok = await run(
    () =>
      submitRegularization({
        empId: form.empId,
        regularDate: form.regularDate,
        comment: form.comment || undefined,
        reason: form.reason || undefined,
      }),
    '转正办理已发起，等待审批',
  );
  if (ok) {
    form.empId = undefined;
    form.comment = '';
    form.reason = '';
  }
}
</script>

<template>
  <a-card :bordered="false">
    <p class="hint">
      仅「试用期」员工可发起。转正日期早于试用期结束日即为提前转正，晚于则为延期转正。
    </p>
    <a-form layout="vertical" :model="form">
      <a-row :gutter="16">
        <a-col :span="8">
          <a-form-item label="员工 ID" required>
            <a-input-number v-model:value="form.empId" :min="1" style="width: 100%" placeholder="试用期员工 ID" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="转正日期" required>
            <a-date-picker v-model:value="form.regularDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="16">
          <a-form-item label="转正评语">
            <a-textarea v-model:value="form.comment" :rows="3" placeholder="试用期表现评价，选填" />
          </a-form-item>
        </a-col>
        <a-col :span="16">
          <a-form-item label="备注">
            <a-textarea v-model:value="form.reason" :rows="2" placeholder="选填" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-button type="primary" :loading="submitting" @click="handleSubmit">发起转正办理</a-button>
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
