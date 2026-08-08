import axios from "axios";

import { API_BASE_URL } from "@/shared/api/config";

import { clearSession, markSession } from "./sessionMarker";
import { setAccessToken } from "./store/authStore";

/**
 * 재발급 결과.
 *
 * <p><b>실패를 둘로 나눈 것이 핵심이다.</b> 서버가 거절한 것("unauthorized")과 서버에 닿지도
 * 못한 것("offline")은 다른 사건인데, 예전에는 둘 다 로그아웃으로 처리해 비행기 모드에서
 * 새로고침만 해도 로그아웃됐다(#1095).
 */
export type ReissueResult = "ok" | "unauthorized" | "offline";

/** 탭 간 재발급 직렬화용 Web Lock 이름. */
const LOCK_NAME = "orino-auth-refresh";
/** 탭 간 토큰/로그아웃 전파용 BroadcastChannel 이름. */
const CHANNEL_NAME = "orino-auth";

interface TokenMessage {
  type: "token";
  accessToken: string;
}
interface LogoutMessage {
  type: "logout";
}
type AuthMessage = TokenMessage | LogoutMessage;

const channel: BroadcastChannel | null =
  typeof BroadcastChannel !== "undefined"
    ? new BroadcastChannel(CHANNEL_NAME)
    : null;

// 다른 탭이 토큰을 갱신/로그아웃하면 이 탭 스토어에도 반영한다 — 그 탭들이 각자 또 재발급하거나
// (토큰 회전 레이스로) 로그아웃당하지 않도록. BroadcastChannel은 같은 인스턴스엔 안 오므로 자기
// 메시지를 자기가 받지 않는다.
channel?.addEventListener("message", (e: MessageEvent<AuthMessage>) => {
  const msg = e.data;
  if (msg?.type === "token") {
    setAccessToken(msg.accessToken);
  } else if (msg?.type === "logout") {
    setAccessToken(null);
  }
});

let inFlight: Promise<ReissueResult> | null = null;

async function reissueOnce(): Promise<ReissueResult> {
  try {
    const { data } = await axios.post<{ data: { accessToken: string } }>(
      `${API_BASE_URL}/auth/reissue`,
      null,
      { withCredentials: true },
    );
    const token = data.data.accessToken;
    setAccessToken(token);
    markSession();
    channel?.postMessage({ type: "token", accessToken: token } as AuthMessage);
    return "ok";
  } catch (error) {
    // 응답이 있으면 서버가 판단을 내린 것이다 — 그때만 로그아웃이다.
    if (axios.isAxiosError(error) && error.response) {
      setAccessToken(null);
      clearSession();
      return "unauthorized";
    }
    // 서버에 닿지 못했다. 토큰도 세션 표시도 건드리지 않는다 — 나중에 다시 물어보면 된다.
    return "offline";
  }
}

/** Web Locks가 있으면 그 락 안에서 실행(브라우저당 동시 재발급 1개), 없으면 그냥 실행한다. */
function withCrossTabLock(
  fn: () => Promise<ReissueResult>,
): Promise<ReissueResult> {
  const locks =
    typeof navigator !== "undefined"
      ? (navigator as Navigator & { locks?: LockManager }).locks
      : undefined;
  if (locks?.request) {
    return locks.request(LOCK_NAME, fn) as Promise<ReissueResult>;
  }
  return fn();
}

/**
 * 엑세스 토큰 재발급. 탭 안에선 진행 중 요청 하나로 합치고(단일 비행), 탭 간에는 Web Lock으로
 * 직렬화한다. 성공하면 새 토큰을 다른 탭에도 전파한다. 인터셉터·앱 시작 재발급이 모두 이 함수를
 * 거치게 해, 리프레시 토큰 회전(단일 사용)이 동시 호출로 서로를 무효화하며 재로그인시키는 걸 막는다.
 */
export function refreshAccessToken(): Promise<ReissueResult> {
  if (inFlight) return inFlight;
  inFlight = withCrossTabLock(reissueOnce).finally(() => {
    inFlight = null;
  });
  return inFlight;
}

/** 로그아웃을 다른 탭에도 알린다(각 탭이 스토어를 비운다). */
export function broadcastLogout(): void {
  channel?.postMessage({ type: "logout" } as AuthMessage);
}
