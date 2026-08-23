import { useQuery } from "@tanstack/react-query";

import { fetchShortlinkTags } from "../api/shortlink";
import { shortlinkKeys } from "../queryKeys";

interface Options {
  /** 기본 true. 링크 워크스페이스가 아닐 때 꺼 둔다. */
  enabled?: boolean;
}

/** 사이드바 태그 섹션. 살아 있는 링크에 붙은 태그만, 개수 많은 순으로 온다. */
export function useShortlinkTags({ enabled = true }: Options = {}) {
  return useQuery({
    queryKey: shortlinkKeys.tags,
    queryFn: fetchShortlinkTags,
    staleTime: 60 * 1000,
    enabled,
  });
}
