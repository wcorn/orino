import axios from "axios";
import { getAccessToken, setAccessToken } from "../auth/authStore";

const client = axios.create({
  baseURL: "/api",
});

client.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
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
      const { data } = await axios.post("/api/auth/reissue");
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
  }
);

export default client;
