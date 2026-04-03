import axios from "axios";
import client from "../../../shared/api/client";
import { setAccessToken } from "../store/authStore";

interface LoginRequest {
  loginId: string;
  password: string;
}

interface TokenResponse {
  code: string;
  data: { accessToken: string };
}

export async function login(request: LoginRequest): Promise<void> {
  const { data } = await client.post<TokenResponse>("/auth/login", request);
  setAccessToken(data.data.accessToken);
}

export async function reissue(): Promise<boolean> {
  try {
    const { data } = await axios.post<TokenResponse>(
      `${client.defaults.baseURL}/auth/reissue`,
      null,
      { withCredentials: true }
    );
    setAccessToken(data.data.accessToken);
    return true;
  } catch {
    setAccessToken(null);
    return false;
  }
}

export async function logout(): Promise<void> {
  try {
    await client.post("/auth/logout");
  } finally {
    setAccessToken(null);
  }
}
