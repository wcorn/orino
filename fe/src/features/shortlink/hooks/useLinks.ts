import { useQuery } from "@tanstack/react-query";

import { fetchLinks, type LinkListParams } from "../api/shortlink";
import { shortlinkKeys } from "../queryKeys";

interface Options extends LinkListParams {
  /**
   * 기본 true. 링크 워크스페이스가 아닐 때는 false로 꺼서 링크 API를 부르지 않는다 —
   * 사이드바처럼 세 워크스페이스가 공유하는 컴포넌트는 훅을 조건부로 호출할 수 없다
   * (여행 요약과 같은 방식).
   */
  enabled?: boolean;
}

/**
 * 링크 목록. 사이드바의 개수와 목록 화면이 <b>같은 캐시</b>를 쓴다 — 필터 없이 부르면
 * 키가 같으므로, 사이드바를 지나온 사용자는 목록이 즉시 그려진다.
 */
export function useLinks({ enabled = true, ...params }: Options = {}) {
  return useQuery({
    queryKey: shortlinkKeys.list(params),
    queryFn: () => fetchLinks(params),
    staleTime: 30 * 1000,
    enabled,
  });
}
