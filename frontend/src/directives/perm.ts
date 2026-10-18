import type { Directive } from 'vue';
import { useUserStore } from '../stores/user';

/**
 * 按钮级权限指令（v-perm="模块:资源:动作"）
 * 无权限时移除元素。数据权限仍由后端强制（前端仅做展示收敛）。
 */
export const vPerm: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    const required = binding.value;
    if (!required) return;
    const userStore = useUserStore();
    if (!userStore.hasPerm(required)) {
      el.parentNode?.removeChild(el);
    }
  },
};
