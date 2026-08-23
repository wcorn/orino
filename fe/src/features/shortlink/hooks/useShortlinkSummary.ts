import { useQuery } from "@tanstack/react-query";

import { fetchShortlinkSummary } from "../api/shortlink";
import { shortlinkKeys } from "../queryKeys";

/**
 * 링크 요약. `/select` 링크 카드의 메타 줄이 쓴다.
 *
 * <p>실패해도 화면은 <b>메타 줄만 비운다</b> — 카드는 그대로 눌린다. 링크 워크스페이스로
 * 들어가는 길이 요약 조회에 걸려 막히면 안 된다.
 */
export function useShortlinkSummary() {
  return useQuery({
    queryKey: shortlinkKeys.summary,
    queryFn: fetchShortlinkSummary,
    staleTime: 60 * 1000,
  });
}
