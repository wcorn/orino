import { client } from "@/shared/api";

export interface GoogleStatus {
  connected: boolean;
  googleEmail: string | null;
  scopes: string[] | null;
  connectedAt: string | null;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/** 연동 상태 조회. 미연동이면 connected=false, 나머지 null. */
export async function fetchGoogleStatus(): Promise<GoogleStatus> {
  const { data } = await client.get<ApiEnvelope<GoogleStatus>>(
    "/planner/google/status",
  );
  return data.data;
}

/** 동의 화면 인증 URL 발급 (서버가 state를 생성·저장). */
export async function fetchGoogleAuthUrl(): Promise<string> {
  const { data } = await client.get<ApiEnvelope<{ authorizationUrl: string }>>(
    "/planner/google/oauth/url",
  );
  return data.data.authorizationUrl;
}

/** 연동 해제 (revoke + 삭제). */
export async function disconnectGoogle(): Promise<void> {
  await client.post("/planner/google/disconnect");
}
