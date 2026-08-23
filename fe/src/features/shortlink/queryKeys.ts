import type { LinkListParams } from "./api/shortlink";

/**
 * 링크 쿼리 키. 기존 {@link travelKeys} 패턴대로 한곳에 묶는다.
 *
 * <p>목록 키에 필터가 들어간다 — 사이드바가 부르는 필터 없는 목록과 화면의 필터된 목록이
 * 같은 캐시를 쓰면, 검색어를 넣는 순간 사이드바 개수가 함께 흔들린다.
 */
export const shortlinkKeys = {
  all: ["shortlink"] as const,
  summary: ["shortlink", "summary"] as const,
  tags: ["shortlink", "tags"] as const,
  lists: ["shortlink", "list"] as const,
  list: (params: LinkListParams = {}) =>
    [
      "shortlink",
      "list",
      params.query ?? "",
      params.status ?? "ALL",
      params.tag ?? "",
    ] as const,
  link: (slug: string) => ["shortlink", "link", slug] as const,
};
