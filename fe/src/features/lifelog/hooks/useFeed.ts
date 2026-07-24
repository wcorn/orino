import { useInfiniteQuery } from "@tanstack/react-query";

import { fetchFeed } from "../api/moments";
import type { FeedFilters } from "../api/types";
import { lifelogKeys } from "../queryKeys";

const PAGE_SIZE = 20;

/** 역시간순 피드(무한 스크롤). tag 필터 선택. */
export function useFeed(filters: FeedFilters = {}) {
  return useInfiniteQuery({
    queryKey: lifelogKeys.feed(filters),
    queryFn: ({ pageParam }) =>
      fetchFeed({ cursor: pageParam, size: PAGE_SIZE, tag: filters.tag }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });
}
