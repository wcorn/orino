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
  /**
   * 공개 base URL(`https://s.orino.dev`). 발급 모달이 **만들기 전에** 「17자」를 보여 주려면
   * 도메인을 알아야 하는데, 링크가 하나도 없으면 기존 `shortUrl`에서 얻을 수 없다.
   * 그렇다고 프론트에 하드코딩하면 환경이 갈릴 때 두 곳이 어긋난다 — 서버가 알려 준다.
   */
  baseUrl: string;
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

/** 발급 응답 = 목록 카드 + QR에 담을 문자열. */
export interface CreatedLink extends LinkSummary {
  qrPayload: string;
}

export interface CreateLinkRequest {
  targetUrl: string;
  /** 비우면 서버가 5자를 자동으로 뽑는다. */
  slug?: string;
  memo?: string;
  tags?: string[];
  expiresAt?: string;
  password?: string;
}

export async function createLink(
  body: CreateLinkRequest,
): Promise<CreatedLink> {
  const { data } = await client.post<ApiEnvelope<CreatedLink>>(
    "/shortlinks",
    body,
  );
  return data.data;
}

/** 활성 ↔ 비활성. 만료된 링크를 켜도 만료가 이긴다(서버가 파생해 준다). */
export async function toggleLink(slug: string): Promise<LinkState> {
  const { data } = await client.post<ApiEnvelope<{ state: LinkState }>>(
    `/shortlinks/${slug}/toggle`,
  );
  return data.data.state;
}

export async function favoriteLink(slug: string): Promise<boolean> {
  const { data } = await client.post<ApiEnvelope<{ favorite: boolean }>>(
    `/shortlinks/${slug}/favorite`,
  );
  return data.data.favorite;
}

/** 소프트 삭제. 이후 그 슬러그는 영구히 점유된다 — 되돌릴 수 없다. */
export async function deleteLink(slug: string): Promise<void> {
  await client.delete(`/shortlinks/${slug}`);
}

/**
 * 커스텀 슬러그 중복 검사. **삭제된 링크의 슬러그도 사용 중**으로 온다 —
 * 살아 있는지 삭제된 것인지는 서버가 구분해 알려주지 않는다.
 */
export async function checkSlugAvailable(slug: string): Promise<boolean> {
  const { data } = await client.get<ApiEnvelope<{ available: boolean }>>(
    "/shortlinks/slug-available",
    { params: { slug } },
  );
  return data.data.available;
}
