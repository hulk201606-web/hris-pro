import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { errorText } from '../crud-helpers';

/**
 * 生命周期办理的公共提交逻辑：四类异动（入职/转正/调动/离职）都需要
 * 「防重复提交 + 成功提示 + 服务端错误原样展示」这套相同处理。
 *
 * 刻意不做前端状态预判（如"该员工是否试用期"）：
 * 状态机规则在服务端，前端若复制一份判断，一旦规则调整就会与后端漂移，
 * 表现为前端放行、后端报错，或更糟——前端拦截了本该允许的操作。
 * 因此一律提交后按服务端返回的错误码与 message 提示。
 */
export function useLifecycleAction() {
  const submitting = ref(false);

  /** 执行提交；返回是否成功，便于调用方决定是否重置表单 */
  async function run(fn: () => Promise<unknown>, okMsg: string): Promise<boolean> {
    if (submitting.value) return false;
    submitting.value = true;
    try {
      await fn();
      message.success(okMsg);
      return true;
    } catch (e) {
      message.error(errorText(e, '提交失败'));
      return false;
    } finally {
      submitting.value = false;
    }
  }

  return { submitting, run };
}
