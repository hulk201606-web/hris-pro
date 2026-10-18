import { theme as antdTheme } from 'ant-design-vue';

/**
 * 智云 HR 主题令牌（单一来源）
 * - 主色沿用 Ant Design Vue 4 默认企业蓝，不引入自定义 hex，
 *   满足「禁硬编码颜色」门禁与「按 AntD 默认蓝 token」选型约束。
 * - 圆角收敛到 6px（与 design-tokens.css 的 --radius-md 对齐）。
 * - 字体采用中文企业级系统字体栈（同 --font-sans），避免 Inter 展示字体套路。
 */
export const appTheme = {
  algorithm: antdTheme.defaultAlgorithm,
  token: {
    borderRadius: 6,
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif",
  },
};
