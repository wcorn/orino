import { client } from "@/shared/api";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/** 링크 상태. 저장값이 아니라 서버가 파생해 주는 값이다 — 화면은 이것만 보고 배지를 정한다. */
export type LinkState = "ACTIVE" | "DISABLED" | "EXPIRED";

/** 목록 카드 하나. 목록·발급·상세가 같은 뼈대를 쓴다. */
export interface LinkSummary {
  slug: string;
  /**
   * 서버가 조립해 준 짧은 주소 전체 문자열. **프론트가 도메인을 만들지 않는다** —
   * 환경마다 호스트가 달라지면 두 곳이 어긋난다.
   */
  shortUrl: string;
  targetUrl: string;
  memo: string | null;
  tags: string[];
  /** 사용자가 슬러그를 직접 지었는지(「커스텀」 배지). */
  custom: boolean;
  favorite: boolean;
  state: LinkState;
  hasPassword: boolean;
  /** 사람 방문만. 통계(#1240) 전까지는 0이다. */
  visitCount: number;
  lastVisitedAt: string | null;
}

export interface LinkListResponse {
  /** 상태 칩 숫자. 상태 필터를 적용하기 전 기준이다. */
  counts: { all: number; active: number; inactive: number };
  favorites: LinkSummary[];
  /** 즐겨찾기를 제외한 나머지 — 같은 카드가 두 섹션에 겹쳐 나오지 않는다. */
  recent: LinkSummary[];
}

export interface ShortlinkSummary {
  total: number;
  /** 통계(#1240) 전까지는 0이다. */
  visitsThisWeek: number;
}

export interface TagCount {
  name: string;
  count: number;
}

export interface LinkListParams {
  query?: string;
  status?: "ALL" | "ACTIVE" | "INACTIVE";
  tag?: string;
}

/** `/select` 링크 카드의 메타 줄. 실패하면 화면은 메타 줄 자체를 그리지 않는다. */
export async function fetchShortlinkSummary(): Promise<ShortlinkSummary> {
  const { data } = await client.get<ApiEnvelope<ShortlinkSummary>>(
    "/shortlinks/summary",
  );
  return data.data;
}

export async function fetchLinks(
  params: LinkListParams = {},
): Promise<LinkListResponse> {
  const { data } = await client.get<ApiEnvelope<LinkListResponse>>(
    "/shortlinks",
    { params },
  );
  return data.data;
}

/** 사이드바 태그 목록. 살아 있는 링크에 붙은 것만 센다. */
export async function fetchShortlinkTags(): Promise<TagCount[]> {
  const { data } =
    await client.get<ApiEnvelope<TagCount[]>>("/shortlinks/tags");
  return data.data;
}
