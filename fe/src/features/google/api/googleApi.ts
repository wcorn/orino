import { client } from "@/shared/api";

export interface GoogleStatus {
  connected: boolean;
  googleEmail: string | null;
  scopes: string[] | null;
  connectedAt: string | null;
  reviewMirrorEnabled: boolean;
}

/** 복습 미러 토글 결과. reviewCalendarId는 OFF여도 보존된다(빠른 재-enable). */
export interface ReviewMirrorStatus {
  enabled: boolean;
  reviewCalendarId: string | null;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/** 연동 상태 조회. 미연동이면 connected=false, 나머지 null. */
export async function fetchGoogleStatus(): Promise<GoogleStatus> {
  const { data } = await client.get<ApiEnvelope<GoogleStatus>>(
    "/integrations/google/status",
  );
  return data.data;
}

/** 동의 화면 인증 URL 발급 (서버가 state를 생성·저장). */
export async function fetchGoogleAuthUrl(): Promise<string> {
  const { data } = await client.get<ApiEnvelope<{ authorizationUrl: string }>>(
    "/integrations/google/oauth/url",
  );
  return data.data.authorizationUrl;
}

/** 연동 해제 (revoke + 삭제). */
export async function disconnectGoogle(): Promise<void> {
  await client.post("/integrations/google/disconnect");
}

/**
 * 복습 → 보조 캘린더 미러 on/off. ON이면 서버가 보조 캘린더 보장 + 전체 백필을 수행하므로 응답까지 다소 걸린다.
 */
export async function setReviewMirror(
  enabled: boolean,
): Promise<ReviewMirrorStatus> {
  const { data } = await client.put<ApiEnvelope<ReviewMirrorStatus>>(
    "/planner/reviews/mirror",
    { enabled },
  );
  return data.data;
}
