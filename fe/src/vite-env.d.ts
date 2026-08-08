/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />
/// <reference types="google.maps" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string;
  readonly VITE_FARO_URL?: string;
  readonly VITE_FARO_KEY?: string;
  /** Maps JavaScript API 브라우저 키. 리퍼러 제한이 걸린 공개 키다(#1102). */
  readonly VITE_GOOGLE_MAPS_API_KEY?: string;
  /** Advanced Marker에 필요한 Map ID. 없으면 마커가 뜨지 않는다. */
  readonly VITE_GOOGLE_MAPS_MAP_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare const __APP_VERSION__: string;

// 이 파일은 모듈이 아니라(import/export 없음) 선언이 곧 전역이다.
// `declare global`로 감싸면 오히려 "모듈 안에서만" 유효해져 적용되지 않는다.
interface Window {
  /** 키가 거부되면 구글이 예외 대신 이걸 부른다(리퍼러 불일치·API 미활성). */
  gm_authFailure?: () => void;
}
