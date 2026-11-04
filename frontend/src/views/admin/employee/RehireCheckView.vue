<script setup lang="ts">
/**
 * 管理端-再入职校验（EMP-F-029）：按证件号查历史任职记录，供录用/建档前查重。
 *
 * 三种命中情况的处置完全不同，因此结果区按优先级区分提示而不是一律报「已存在」：
 *   命中黑名单（7）        -> 禁止录用，须走黑名单解除流程
 *   命中在册（0/1/2/3）    -> 属于重复建档，应直接使用现有档案，不要再建一份
 *   仅命中已离场（4/5/6）  -> 曾任职但已离开，可走二次雇佣（工龄是否连续由业务决定）
 *
 * 实现提示：后端按 cert_no_cipher 等值查询，该列当前按明文落库；
 * 若将来改为随机 IV 加密，查询会静默返回空，需改由 cert_no_hash 列查询。
 */
import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import { checkRehire } from '../../../api/employee';
import type { RehireCheckResponse } from '../../../types/api';

/** 在册状态：这些状态说明该人当前已在公司体系内 */
const ON_BOARD = new Set([0, 1, 2, 3]);
/** 已离场状态：可走二次雇佣 */
const LEFT = new Set([4, 5, 6]);
const BLACKLISTED = 7;

const certNo = ref('');
const result = ref<RehireCheckResponse | null>(null);
const loading = ref(false);

const hasBlacklist = computed(
  () => result.value?.histories?.some((h) => h.status === BLACKLISTED || h.blacklisted === 1) ?? false,
);
const hasOnBoard = computed(
  () => result.value?.histories?.some((h) => ON_BOARD.has(h.status)) ?? false,
);
const onlyLeft = computed(() => {
  const list = result.value?.histories ?? [];
  return list.length > 0 && list.every((h) => LEFT.has(h.status) && h.blacklisted !== 1);
});

async function handleCheck() {
  const v = certNo.value.trim();
  if (!v) return message.warning('请输入证件号');
  loading.value = true;
  result.value = null;
  try {
    result.value = await checkRehire(v);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '查询失败');
  } finally {
    loading.value = false;
  }
}

function reset() {
  certNo.value = '';
  result.value = null;
}
</script>

<template>
  <div class="rehire">
    <section class="head">
      <h2 class="head-title">再入职校验</h2>
      <p class="head-sub">
        录入证件号后查询历史任职记录。命中黑名单禁止录用；命中在册说明是重复建档；
        仅命中已离场则走二次雇佣。
      </p>
    </section>

    <a-card :bordered="false" class="query-card">
      <a-space>
        <span>证件号</span>
        <a-input
          v-model:value="certNo"
          placeholder="请输入完整证件号"
          style="width: 320px"
          allow-clear
          @press-enter="handleCheck"
        />
        <a-button type="primary" :loading="loading" @click="handleCheck">查询</a-button>
        <a-button @click="reset">清空</a-button>
      </a-space>
    </a-card>

    <template v-if="result">
      <a-alert
        v-if="hasBlacklist"
        type="error"
        show-icon
        class="result-alert"
        message="命中黑名单，禁止录用"
        description="该证件号存在黑名单记录。如需录用，须先完成黑名单解除流程。"
      />
      <a-alert
        v-else-if="hasOnBoard"
        type="info"
        show-icon
        class="result-alert"
        message="该人当前在册，属重复建档"
        description="请直接使用现有员工档案，不要新建。同一人多份档案会导致工龄、合同与薪酬记录分裂。"
      />
      <a-alert
        v-else-if="onlyLeft"
        type="warning"
        show-icon
        class="result-alert"
        message="曾在本公司任职，可走二次雇佣"
        description="以下记录均已离场。二次雇佣时工龄是否连续按公司政策决定，需在入职办理时确认。"
      />
      <a-alert
        v-else
        type="success"
        show-icon
        class="result-alert"
        message="未发现历史任职记录"
        description="该证件号在本系统内无任职记录，可正常建档并办理入职。"
      />

      <a-table
        v-if="result.histories && result.histories.length"
        :data-source="result.histories"
        row-key="empId"
        size="middle"
        :pagination="false"
      >
        <a-table-column key="empNo" title="工号" data-index="empNo" :width="180" />
        <a-table-column key="name" title="姓名" data-index="name" :width="140" />
        <a-table-column key="statusLabel" title="上次状态" :width="130">
          <template #default="{ record }">
            <a-tag :color="record.status === BLACKLISTED ? 'red' : LEFT.has(record.status) ? 'default' : 'blue'">
              {{ record.statusLabel }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column key="blacklisted" title="黑名单" :width="110">
          <template #default="{ record }">
            <a-tag v-if="record.blacklisted === 1 || record.status === BLACKLISTED" color="red">是</a-tag>
            <span v-else>否</span>
          </template>
        </a-table-column>
        <a-table-column key="empId" title="员工 ID" :width="200" />
      </a-table>
    </template>
  </div>
</template>

<style scoped>
.rehire {
  padding: 4px;
}
.head {
  margin-bottom: 16px;
}
.head-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}
.head-sub {
  margin: 0;
  color: var(--color-text-secondary, #666);
  font-size: 13px;
}
.query-card {
  margin-bottom: 16px;
}
.result-alert {
  margin-bottom: 16px;
}
</style>
