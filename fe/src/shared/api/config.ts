/** API 기본 URL. 순환 import를 피하려고 client와 분리한 leaf 모듈(다른 걸 import하지 않는다). */
export const API_BASE_URL =
  import.meta.env.VITE_API_URL ?? "https://api.orino.dev/api";
