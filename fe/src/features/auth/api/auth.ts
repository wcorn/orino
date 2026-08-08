import client from "../../../shared/api/client";
import {
  broadcastLogout,
  refreshAccessToken,
  type ReissueResult,
} from "../refreshCoordinator";
import { clearSession, markSession } from "../sessionMarker";
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
  // 이 기기에서 로그인했다는 표시. 오프라인 새로고침이 이걸 근거로 삼는다(#1095).
  markSession();
}

/**
 * 엑세스 토큰 재발급. 코디네이터에 위임한다 — 인터셉터·앱 시작 재발급이 같은 단일 비행/Web Lock을
 * 공유해, 동시 호출이 리프레시 토큰 회전으로 서로를 무효화하는 걸 막는다.
 */
export function reissue(): Promise<ReissueResult> {
  return refreshAccessToken();
}

export async function logout(): Promise<void> {
  try {
    await client.post("/auth/logout");
  } finally {
    setAccessToken(null);
    // 사용자가 직접 나간 것이다 — 오프라인이어도 이 기기의 세션은 끝났다.
    clearSession();
    broadcastLogout();
  }
}
