<script setup lang="ts">
/**
 * 考勤组成员管理抽屉（批 6 #10）：员工多选后保存，走全量覆盖语义
 * POST /att/groups/{id}/members（body {empIds}，empIds 即最终成员，返回落库后的 ID 列表）。
 * 成员初值经 GET /att/groups/{id}/members 回填；候选员工取花名册（GET /emp/employees）。
 */
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import EmptyHint from './EmptyHint.vue';
import { errorText } from './crud-helpers';
import { listGroupMembers, setGroupMembers } from '../../api/attendance';
import { listRoster } from '../../api/employee';
import type { EmployeeBrief } from '../../types/api-employee';

const props = defineProps<{
  /** 考勤组 ID（att_group.id） */
  groupId: number;
  /** 组名（抽屉标题展示用） */
  groupName?: string;
  /** 是否可写（保存按钮），对应 att:group:write */
  canWrite: boolean;
}>();

const emit = defineEmits<{ (e: 'close'): void }>();

const loading = ref(false);
const saving = ref(false);
const memberIds = ref<number[]>([]);
const employees = ref<EmployeeBrief[]>([]);

const options = computed(() =>
  employees.value.map((e) => ({ label: `${e.name}（${e.empNo}）`, value: e.id })),
);

async function load() {
  loading.value = true;
  try {
    const [members, roster] = await Promise.all([
      listGroupMembers(props.groupId),
      listRoster(1, 200),
    ]);
    memberIds.value = members;
    employees.value = roster.records;
  } catch (e) {
    message.error(errorText(e, '成员信息加载失败'));
  } finally {
    loading.value = false;
  }
}

/** 保存即全量覆盖：未勾选的既有成员将被移出 */
async function save() {
  saving.value = true;
  try {
    const finalIds = await setGroupMembers(props.groupId, memberIds.value);
    message.success(`成员已保存（共 ${finalIds.length} 人）`);
    emit('close');
  } catch (e) {
    message.error(errorText(e, '成员保存失败'));
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <a-drawer
    open
    :title="`成员管理${groupName ? '：' + groupName : ''}`"
    width="480"
    :mask-closable="true"
    @close="emit('close')"
  >
    <a-spin :spinning="loading">
      <p class="member-tip">保存即全量覆盖：勾选的员工列表将作为该考勤组的最终成员</p>
      <a-select
        v-model:value="memberIds"
        mode="multiple"
        style="width: 100%"
        placeholder="选择员工（姓名 / 工号）"
        :options="options"
        option-filter-prop="label"
        allow-clear
        :max-tag-count="6"
      />
      <EmptyHint
        v-if="!loading && !employees.length"
        description="暂无可选员工"
        hint="请先在员工花名册建档后再维护组成员"
      />
    </a-spin>

    <template #footer>
      <a-space>
        <a-button @click="emit('close')">取消</a-button>
        <a-button v-if="canWrite" type="primary" :loading="saving" @click="save">
          保存（全量覆盖）
        </a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<style scoped>
.member-tip {
  margin: 0 0 12px;
  color: var(--color-text-secondary);
}
</style>
