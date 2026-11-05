<script setup lang="ts">

/**
 * 我的信息页（SELF-F-002 / EMP-F-021）。
 * - 非关键字段（姓名/手机/邮箱/地址/紧急联系人/紧急电话）走 PATCH /emp/me/profile 直改生效；
 * - 关键字段（证件类型/证件号/银行卡/雇佣类型）只读展示，变更需发起 EMP_PROFILE_CHG 审批；
 * - 提交成功后重新拉取 GET /emp/me/profile 刷新展示。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import {
  fetchSelfProfile,
  requestCriticalChange,
  updateSelfProfile,
} from '../../api/self';
import { BizError } from '../../api/error';
import { useUserStore } from '../../stores/user';
import type { SelfProfile } from '../../types/api';

const userStore = useUserStore();
const canWrite = computed(() => userStore.hasPerm('emp:employee:write'));

const profile = ref<SelfProfile | null>(null);
const loading = ref(true);
const saving = ref(false);
const submitting = ref(false);

/** 可直接编辑字段（空值表示本次不修改，手机号为空则不改密文） */
const form = reactive({
  name: '',
  mobile: '',
  email: '',
  address: '',
  emergencyContact: '',
  emergencyPhone: '',
});

/** 关键字段变更申请表单 */
const changeOpen = ref(false);
const changeForm = reactive({
  certType: '',
  certNo: '',
  bankAccount: '',
  hireType: '',
  reason: '',
});

const hireTypeOptions = [
  { label: '正式', value: 'FULL_TIME' },
  { label: '实习', value: 'INTERN' },
  { label: '外包', value: 'OUTSOURCE' },
];

const certTypeOptions = [
  { label: '身份证', value: 'ID_CARD' },
  { label: '护照', value: 'PASSPORT' },
  { label: '港澳台通行证', value: 'HK_MO_TW' },
];

function errMsg(err: unknown, fallback: string): string {
  return err instanceof BizError ? err.message : fallback;
}

function fillForm(p: SelfProfile) {
  form.name = p.name ?? '';
  form.mobile = '';
  form.email = p.email ?? '';
  form.address = p.address ?? '';
  form.emergencyContact = p.emergencyContact ?? '';
  form.emergencyPhone = p.emergencyPhone ?? '';
}

async function load() {
  loading.value = true;
  try {
    const data = await fetchSelfProfile();
    profile.value = data;
    fillForm(data);
  } catch (err) {
    message.error(errMsg(err, '档案加载失败'));
  } finally {
    loading.value = false;
  }
}

async function save() {
  if (!form.name.trim()) {
    message.warning('姓名不能为空');
    return;
  }
  const payload: Record<string, string> = {};
  if (form.name.trim()) payload.name = form.name.trim();
  if (form.mobile.trim()) payload.mobile = form.mobile.trim();
  if (form.email.trim()) payload.email = form.email.trim();
  payload.address = form.address.trim();
  if (form.emergencyContact.trim()) payload.emergencyContact = form.emergencyContact.trim();
  if (form.emergencyPhone.trim()) payload.emergencyPhone = form.emergencyPhone.trim();

  saving.value = true;
  try {
    profile.value = await updateSelfProfile(payload);
    fillForm(profile.value);
    message.success('已保存并生效');
  } catch (err) {
    message.error(errMsg(err, '保存失败'));
  } finally {
    saving.value = false;
  }
}

function openChange() {
  changeForm.certType = '';
  changeForm.certNo = '';
  changeForm.bankAccount = '';
  changeForm.hireType = '';
  changeForm.reason = '';
  changeOpen.value = true;
}

async function submitChange() {
  const payload: Record<string, string> = {};
  if (changeForm.certType) payload.certType = changeForm.certType;
  if (changeForm.certNo.trim()) payload.certNo = changeForm.certNo.trim();
  if (changeForm.bankAccount.trim()) payload.bankAccount = changeForm.bankAccount.trim();
  if (changeForm.hireType) payload.hireType = changeForm.hireType;
  if (changeForm.reason.trim()) payload.reason = changeForm.reason.trim();
  if (!payload.certType && !payload.certNo && !payload.bankAccount && !payload.hireType) {
    message.warning('请至少填写一个需要变更的关键字段');
    return;
  }
  submitting.value = true;
  try {
    const res = await requestCriticalChange(payload);
    changeOpen.value = false;
    message.success(`已提交 ${res.templateKey} 流程，受理单号 ${res.bizKey}`);
    await load();
  } catch (err) {
    message.error(errMsg(err, '变更申请提交失败'));
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <a-spin :spinning="loading">
    <div class="my-profile">
      <a-card :bordered="false" class="profile-card" title="基本档案">
        <template #extra>
          <span class="hint">证件号与手机号已脱敏展示，变更需走审批</span>
        </template>
        <a-descriptions v-if="profile" :column="{ xs: 1, sm: 2, md: 3 }">
          <a-descriptions-item label="工号">{{ profile.empNo || '—' }}</a-descriptions-item>
          <a-descriptions-item label="姓名">{{ profile.name || '—' }}</a-descriptions-item>
          <a-descriptions-item label="雇佣类型">{{ profile.hireType || '—' }}</a-descriptions-item>
          <a-descriptions-item label="部门 ID">{{ profile.deptId ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="岗位 ID">{{ profile.positionId ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="手机号">{{ profile.mobileMask || '—' }}</a-descriptions-item>
        </a-descriptions>
        <a-empty v-else description="暂无档案信息" />
      </a-card>

      <a-card :bordered="false" class="profile-card" title="可自助修改">
        <template #extra>
          <span class="hint">提交后立即生效</span>
        </template>
        <a-form :model="form" layout="vertical" :disabled="!canWrite">
          <a-row :gutter="16">
            <a-col :xs="24" :sm="12" :md="8">
              <a-form-item label="姓名" required>
                <a-input v-model:value="form.name" placeholder="请输入姓名" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="8">
              <a-form-item label="手机号">
                <a-input
                  v-model:value="form.mobile"
                  :placeholder="`留空不修改，当前 ${profile?.mobileMask || '未登记'}`"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="8">
              <a-form-item label="邮箱">
                <a-input v-model:value="form.email" placeholder="name@example.com" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="24" :md="12">
              <a-form-item label="通讯地址">
                <a-input v-model:value="form.address" placeholder="请输入通讯地址" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="6">
              <a-form-item label="紧急联系人">
                <a-input v-model:value="form.emergencyContact" placeholder="联系人姓名" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="6">
              <a-form-item label="紧急联系电话">
                <a-input v-model:value="form.emergencyPhone" placeholder="联系人电话" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-space>
            <a-button v-if="canWrite" type="primary" :loading="saving" @click="save">保存</a-button>
            <a-button @click="load">重置</a-button>
          </a-space>
        </a-form>
      </a-card>

      <a-card :bordered="false" class="profile-card" title="关键信息（需审批）">
        <template #extra>
          <a-button v-if="canWrite" type="link" @click="openChange">
            <template #icon><PlusOutlined /></template>
            申请变更
          </a-button>
        </template>
        <a-descriptions :column="{ xs: 1, sm: 2 }">
          <a-descriptions-item label="证件号">{{ profile?.certNoMask || '—' }}</a-descriptions-item>
          <a-descriptions-item label="证件类型">以档案登记为准</a-descriptions-item>
          <a-descriptions-item label="银行卡号">以档案登记为准</a-descriptions-item>
          <a-descriptions-item label="雇佣类型">{{ profile?.hireType || '—' }}</a-descriptions-item>
        </a-descriptions>
        <p class="hint">证件号 / 证件类型 / 银行卡 / 雇佣类型不可直接修改，需发起 EMP_PROFILE_CHG 审批。</p>
      </a-card>
    </div>

    <a-modal
      v-model:open="changeOpen"
      title="申请关键信息变更"
      :confirm-loading="submitting"
      :mask-closable="false"
      ok-text="提交申请"
      cancel-text="取消"
      @ok="submitChange"
    >
      <a-form :model="changeForm" layout="vertical">
        <a-form-item label="证件类型">
          <a-select v-model:value="changeForm.certType" :options="certTypeOptions" allow-clear placeholder="不修改则留空" />
        </a-form-item>
        <a-form-item label="证件号">
          <a-input v-model:value="changeForm.certNo" placeholder="不修改则留空" />
        </a-form-item>
        <a-form-item label="银行卡号">
          <a-input v-model:value="changeForm.bankAccount" placeholder="不修改则留空" />
        </a-form-item>
        <a-form-item label="雇佣类型">
          <a-select v-model:value="changeForm.hireType" :options="hireTypeOptions" allow-clear placeholder="不修改则留空" />
        </a-form-item>
        <a-form-item label="变更原因">
          <a-textarea v-model:value="changeForm.reason" :rows="3" placeholder="说明变更原因，便于审批人核对" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-spin>
</template>

<style scoped>
.my-profile {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.profile-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.hint {
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
