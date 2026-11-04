<script setup lang="ts">
/**
 * 入职办理表单（EMP-F-003/004/006）。
 *
 * 前置：员工必须已是「待入职（PENDING）」，即先在花名册建档。
 * 提交后服务端依次做：编制校验（超编返回 51204）→ 创建 EMP_ONBOARD 单
 * → 入职生效时落状态与状态流水 → 通过 AccountIntegration 联动开通账号
 * （账号开通失败不阻断入职，仅降级）。
 *
 * hireType 取值以数据库口径为准：FULLTIME / PARTTIME / INTERN / CONSULTANT
 * （见 docs/db/schema-mysql.sql 的 emp_employment.hire_type 注释）。
 */
import { reactive } from 'vue';
import { message } from 'ant-design-vue';
import { submitOnboarding } from '../../../api/employee';
import { useLifecycleAction } from './useLifecycleAction';

const props = defineProps<{
  deptOptions: { value: number; label: string }[];
  positionOptions: { value: number; label: string }[];
  gradeOptions: { value: number; label: string }[];
  legalEntityOptions: { value: number; label: string }[];
}>();

const hireTypeOptions = [
  { value: 'FULLTIME', label: '全职' },
  { value: 'PARTTIME', label: '兼职' },
  { value: 'INTERN', label: '实习' },
  { value: 'CONSULTANT', label: '劳务 / 顾问' },
];

const form = reactive({
  empId: undefined as number | undefined,
  hireDate: '',
  hireType: 'FULLTIME',
  legalEntityId: undefined as number | undefined,
  deptId: undefined as number | undefined,
  positionId: undefined as number | undefined,
  gradeId: undefined as number | undefined,
  supervisorEmpId: undefined as number | undefined,
  username: '',
  password: '',
  reason: '',
});

const { submitting, run } = useLifecycleAction();

async function handleSubmit() {
  if (!form.empId) return message.warning('请填写员工 ID');
  if (!form.hireDate) return message.warning('请选择入职日期');
  if (!form.legalEntityId) return message.warning('请选择法人主体');
  if (!form.deptId) return message.warning('请选择部门');
  if (!form.positionId) return message.warning('请选择岗位');
  if (!form.hireType) return message.warning('请选择雇佣类型');

  const ok = await run(
    () =>
      submitOnboarding({
        empId: form.empId,
        hireDate: form.hireDate,
        hireType: form.hireType,
        legalEntityId: form.legalEntityId,
        deptId: form.deptId,
        positionId: form.positionId,
        gradeId: form.gradeId,
        supervisorEmpId: form.supervisorEmpId,
        // 账号与密码留空时由服务端回退：用户名取工号，密码随机生成
        username: form.username || undefined,
        password: form.password || undefined,
        reason: form.reason || undefined,
      }),
    '入职办理已发起，等待审批',
  );
  if (ok) {
    form.empId = undefined;
    form.username = '';
    form.password = '';
    form.reason = '';
  }
}
</script>

<template>
  <a-card :bordered="false">
    <p class="hint">员工需先建档为「待入职」。用户名/密码留空时，服务端用工号与随机初密开通账号。</p>
    <a-form layout="vertical" :model="form">
      <a-row :gutter="16">
        <a-col :span="8">
          <a-form-item label="员工 ID" required>
            <a-input-number v-model:value="form.empId" :min="1" style="width: 100%" placeholder="待入职员工 ID" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="入职日期" required>
            <a-date-picker v-model:value="form.hireDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="雇佣类型" required>
            <a-select v-model:value="form.hireType" :options="hireTypeOptions" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="法人主体" required>
            <a-select
              v-model:value="form.legalEntityId"
              :options="props.legalEntityOptions"
              placeholder="请选择法人主体"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="部门" required>
            <a-select
              v-model:value="form.deptId"
              :options="props.deptOptions"
              placeholder="请选择部门"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="岗位" required>
            <a-select
              v-model:value="form.positionId"
              :options="props.positionOptions"
              placeholder="请选择岗位"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="职级">
            <a-select
              v-model:value="form.gradeId"
              :options="props.gradeOptions"
              allow-clear
              placeholder="选填"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="直属上级 ID">
            <a-input-number v-model:value="form.supervisorEmpId" :min="1" style="width: 100%" placeholder="选填" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="登录名">
            <a-input v-model:value="form.username" placeholder="留空则用工号" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="初始密码">
            <a-input-password v-model:value="form.password" placeholder="留空则随机生成" />
          </a-form-item>
        </a-col>
        <a-col :span="16">
          <a-form-item label="备注">
            <a-textarea v-model:value="form.reason" :rows="2" placeholder="选填" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-button type="primary" :loading="submitting" @click="handleSubmit">发起入职办理</a-button>
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
