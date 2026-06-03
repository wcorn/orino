import axios from "axios";

import {
  getAccessToken,
  setAccessToken,
} from "../../features/auth/store/authStore";

export const API_BASE_URL =
  import.meta.env.VITE_API_URL ?? "https://api.orino.dev/api";

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

let isRefreshing = false;
let pendingRequests: Array<() => void> = [];

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise((resolve) => {
        pendingRequests.push(() => resolve(client(originalRequest)));
      });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const { data } = await axios.post(`${API_BASE_URL}/auth/reissue`, null, {
        withCredentials: true,
      });
      setAccessToken(data.data.accessToken);
      pendingRequests.forEach((cb) => cb());
      return client(originalRequest);
    } catch {
      setAccessToken(null);
      window.location.href = "/login";
      return Promise.reject(error);
    } finally {
      isRefreshing = false;
      pendingRequests = [];
    }
  },
);

export default client;
