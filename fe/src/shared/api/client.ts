import axios from "axios";

import { refreshAccessToken } from "../../features/auth/refreshCoordinator";
import { getAccessToken } from "../../features/auth/store/authStore";
import { API_BASE_URL } from "./config";

export { API_BASE_URL };

const client = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
});

// 사용자 브라우저의 IANA 시간대. 서버는 이 값으로 저장된 UTC 시각을
// 사용자 로컬 기준(응답 변환 + 복습 4시 롤오버 계산)으로 처리한다.
const userTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

client.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  if (userTimeZone) {
    config.headers["X-Timezone"] = userTimeZone;
  }
  return config;
});

/** "Bearer xxx" → "xxx". 그 외엔 undefined. */
function bearerToken(auth: unknown): string | undefined {
  return typeof auth === "string" && auth.startsWith("Bearer ")
    ? auth.slice(7)
    : undefined;
}

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (
      error.response?.status !== 401 ||
      !originalRequest ||
      originalRequest._retry
    ) {
      return Promise.reject(error);
    }
    originalRequest._retry = true;

    // 다른 탭·다른 요청이 이미 토큰을 갱신했으면(스토어 토큰이 이 요청에 쓴 것과 다름) 재발급 없이
    // 최신 토큰으로 바로 재시도한다 — 불필요한 재발급(=회전)을 아낀다.
    const usedToken = bearerToken(originalRequest.headers?.Authorization);
    const current = getAccessToken();
    if (current && current !== usedToken) {
      return client(originalRequest);
    }

    // 재발급은 코디네이터가 탭 내 단일 비행 + 탭 간 Web Lock으로 직렬화한다.
    const result = await refreshAccessToken();
    if (result === "ok") {
      return client(originalRequest);
    }
    // 재발급조차 못 닿았으면 네트워크가 없는 것이다. 로그인 화면으로 쫓아내면 캐시로
    // 보던 화면까지 잃는다(#1095) — 그냥 실패로 돌려주고 화면이 오프라인 처리를 하게 둔다.
    if (result === "offline") {
      return Promise.reject(error);
    }
    window.location.href = "/login";
    return Promise.reject(error);
  },
);

export default client;
