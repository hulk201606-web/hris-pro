/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 应用标题 */
  readonly VITE_APP_TITLE: string;
  /** API 基础路径（网关前缀） */
  readonly VITE_API_BASE_URL: string;
  /** 是否启用前端静态 Mock（后端未就绪时） */
  readonly VITE_USE_MOCK: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
