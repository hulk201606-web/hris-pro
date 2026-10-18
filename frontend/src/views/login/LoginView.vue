<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { ApartmentOutlined, LockOutlined, UserOutlined } from '@ant-design/icons-vue';
import { useUserStore } from '../../stores/user';
import { BizError } from '../../api/error';
import type { LoginRequest } from '../../types/api';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const formState = reactive<LoginRequest>({ tenantCode: '', username: '', password: '' });
const loading = ref(false);

const rules = {
  tenantCode: [{ required: true, message: '请输入租户编码' }],
  username: [{ required: true, message: '请输入账号' }],
  password: [{ required: true, message: '请输入密码' }],
};

async function handleSubmit() {
  loading.value = true;
  try {
    await userStore.login(formState);
    await userStore.loadProfile();
    message.success('登录成功');
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/self/home';
    router.replace(redirect);
  } catch (err) {
    const msg = err instanceof BizError ? err.message : '登录失败，请检查租户、账号或密码';
    message.error(msg);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <a-card class="login-card" :bordered="false">
      <div class="brand">
        <h1 class="brand-title">智云 HR</h1>
        <p class="brand-sub">多租户 SaaS 人力资源管理系统</p>
        <p class="brand-tagline">员工自助服务中心</p>
      </div>

      <a-form
        :model="formState"
        :rules="rules"
        layout="vertical"
        @finish="handleSubmit"
      >
        <a-form-item name="tenantCode">
          <a-input v-model:value="formState.tenantCode" size="large" placeholder="租户编码">
            <template #prefix><ApartmentOutlined /></template>
          </a-input>
        </a-form-item>

        <a-form-item name="username">
          <a-input v-model:value="formState.username" size="large" placeholder="账号">
            <template #prefix><UserOutlined /></template>
          </a-input>
        </a-form-item>

        <a-form-item name="password">
          <a-input-password v-model:value="formState.password" size="large" placeholder="密码">
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>

        <a-button type="primary" html-type="submit" size="large" block :loading="loading">
          登录
        </a-button>
      </a-form>

      <p class="login-footnote">首次使用请联系管理员获取租户编码与账号</p>
    </a-card>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--space-6);
  background: var(--color-bg-page);
}

.login-card {
  width: 380px;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
}

.brand {
  margin-bottom: var(--space-6);
  text-align: center;
}

.brand-title {
  margin: 0;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: var(--ls-heading);
  color: var(--color-text-primary);
}

.brand-sub {
  margin: var(--space-2) 0 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.brand-tagline {
  margin: var(--space-2) 0 0;
  font-size: 12px;
  letter-spacing: var(--ls-caps);
  color: var(--color-text-muted);
}

.login-footnote {
  margin: var(--space-5) 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
