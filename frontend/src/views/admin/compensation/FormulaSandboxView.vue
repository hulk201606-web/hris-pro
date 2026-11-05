<script setup lang="ts">
/**
 * 管理端-公式沙箱页（CMP-F-021）：Aviator 公式编译校验 + 样例变量试算，
 * 并展示后端下发的变量/函数白名单说明。权限复用 cmp:payroll-run:write。
 */
import { reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { CaretRightOutlined, PlusOutlined } from '@ant-design/icons-vue';
import EmptyHint from '../../../components/admin/EmptyHint.vue';
import { errorText } from '../../../components/admin/crud-helpers';
import { validateFormula } from '../../../api/compensation';
import { useUserStore } from '../../../stores/user';
import type { FormulaValidateResponse } from '../../../types/api-batch3';

const userStore = useUserStore();
const canWrite = userStore.hasPerm('cmp:payroll-run:write');

const formula = ref('round(base * 0.1 + lateCount * 50, 2)');
const validating = ref(false);
const result = ref<FormulaValidateResponse | null>(null);

/** 样例变量编辑行（key + value，value 尽量转数值） */
interface VarRow {
  key: string;
  value: string;
}
const varRows = reactive<VarRow[]>([
  { key: 'base', value: '10000' },
  { key: 'lateCount', value: '2' },
]);

function addRow() {
  varRows.push({ key: '', value: '' });
}

function removeRow(index: number) {
  varRows.splice(index, 1);
}

function buildSampleVars(): Record<string, unknown> | undefined {
  const vars: Record<string, unknown> = {};
  for (const row of varRows) {
    const key = row.key.trim();
    if (!key) continue;
    const num = Number(row.value);
    vars[key] = row.value.trim() !== '' && Number.isFinite(num) ? num : row.value;
  }
  return Object.keys(vars).length ? vars : undefined;
}

async function handleValidate() {
  const f = formula.value.trim();
  if (!f) {
    message.warning('请先填写公式');
    return;
  }
  validating.value = true;
  result.value = null;
  try {
    result.value = await validateFormula(f, buildSampleVars());
  } catch (e) {
    message.error(errorText(e, '校验请求失败'));
  } finally {
    validating.value = false;
  }
}
</script>

<template>
  <div class="sandbox-page">
    <section class="head">
      <div>
        <h2 class="head-title">公式沙箱</h2>
        <p class="head-sub">保存薪资项公式前，先在此做编译校验与样例试算，避免线上算薪失败</p>
      </div>
      <a-button v-if="canWrite" type="primary" :loading="validating" @click="handleValidate">
        <template #icon><CaretRightOutlined /></template>
        试算
      </a-button>
    </section>

    <a-row :gutter="16">
      <a-col :span="14">
        <a-card :bordered="false" class="panel-card" title="公式与样例变量">
          <a-form layout="vertical">
            <a-form-item label="公式（Aviator 表达式）">
              <a-textarea v-model:value="formula" :rows="4" placeholder="如 round(base * 0.1 + lateCount * 50, 2)" />
            </a-form-item>
            <a-form-item label="样例变量（可留空，后端将使用内置演示值）">
              <div v-for="(row, idx) in varRows" :key="idx" class="var-row">
                <a-input v-model:value="row.key" placeholder="变量名，如 bonus" style="width: 180px" />
                <a-input v-model:value="row.value" placeholder="值，如 2000" style="width: 160px" />
                <a-button type="link" danger size="small" @click="removeRow(idx)">移除</a-button>
              </div>
              <a-button type="dashed" size="small" @click="addRow">
                <template #icon><PlusOutlined /></template>
                添加变量
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>
      <a-col :span="10">
        <a-card :bordered="false" class="panel-card" title="试算结果">
          <template v-if="result">
            <a-alert
              :type="result.valid ? 'success' : 'error'"
              :message="result.message"
              show-icon
            />
            <div v-if="result.valid && result.result" class="result-value">
              <span class="result-label">结果</span>
              <code>{{ result.result }}</code>
            </div>
            <div v-if="result.variables.length" class="result-vars">
              引用变量：<a-tag v-for="v in result.variables" :key="v">{{ v }}</a-tag>
            </div>
          </template>
          <EmptyHint v-else description="尚未试算" hint="填写公式与样例变量后点击右上角「试算」" />
        </a-card>
      </a-col>
    </a-row>

    <a-card :bordered="false" class="panel-card" title="白名单说明">
      <a-row :gutter="16">
        <a-col :span="12">
          <h4 class="wl-title">可用变量</h4>
          <ul class="wl-list">
            <li><code>base</code>：定薪基本额（员工薪酬档）</li>
            <li><code>lateCount</code>：当月迟到次数（考勤联动接入前固定 0）</li>
            <li><code>absentDays</code>：当月旷工天数（考勤联动接入前固定 0）</li>
            <li>前序薪资项编码：按顺序引用已计算完成的薪资项结果</li>
          </ul>
        </a-col>
        <a-col :span="12">
          <h4 class="wl-title">可用函数</h4>
          <ul class="wl-list">
            <li><code>round(x, n)</code>：四舍五入保留 n 位小数（金额建议收口到 2 位）</li>
            <li><code>min(x, y)</code> / <code>max(x, y)</code>：取小 / 取大</li>
            <li>
              <code>taxMonthly(taxable)</code>：按月度累计税率表计算个税；入参为应纳税所得额
              （公式侧需先自行减除 5000 起征点）
            </li>
          </ul>
        </a-col>
      </a-row>
      <p class="wl-note">
        说明：沙箱禁止 new 实例与静态方法调用，除白名单外的变量与函数将在求值时报错；
        数值按 BigDecimal 精确计算，避免浮点误差。
      </p>
    </a-card>
  </div>
</template>

<style scoped>
.sandbox-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
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

.panel-card {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.var-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.result-value {
  margin-top: 12px;
}

.result-label {
  margin-right: 8px;
  color: var(--color-text-secondary);
}

.result-vars {
  margin-top: 12px;
}

.wl-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.wl-list {
  margin: 0;
  padding-left: 18px;
  color: var(--color-text-secondary);
  line-height: 2;
}

.wl-note {
  margin: 16px 0 0;
  font-size: 13px;
  color: var(--color-text-tertiary, var(--color-text-secondary));
}
</style>
