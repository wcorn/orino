import client from "../../../shared/api/client";
import { broadcastLogout, refreshAccessToken } from "../refreshCoordinator";
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

/**
 * 엑세스 토큰 재발급. 코디네이터에 위임한다 — 인터셉터·앱 시작 재발급이 같은 단일 비행/Web Lock을
 * 공유해, 동시 호출이 리프레시 토큰 회전으로 서로를 무효화하는 걸 막는다.
 */
export function reissue(): Promise<boolean> {
  return refreshAccessToken();
}

export async function logout(): Promise<void> {
  try {
    await client.post("/auth/logout");
  } finally {
    setAccessToken(null);
    broadcastLogout();
  }
}
