import { useQuery } from "@tanstack/react-query";

import { fetchMemo } from "../api/memos";
import { memoKeys } from "../queryKeys";

export function useMemoDetail(memoId: number | null) {
  return useQuery({
    queryKey: memoKeys.detail(memoId ?? 0),
    queryFn: () => fetchMemo(memoId as number),
    enabled: memoId !== null,
    staleTime: Infinity,
  });
}
