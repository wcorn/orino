import { useQuery } from "@tanstack/react-query";

import { fetchTagSuggestions } from "../api/moments";
import { lifelogKeys } from "../queryKeys";

/** 태그 자동완성. 입력이 있을 때만 조회한다. */
export function useTagSuggestions(query: string) {
  const trimmed = query.trim();
  return useQuery({
    queryKey: lifelogKeys.tags(trimmed),
    queryFn: () => fetchTagSuggestions(trimmed),
    enabled: trimmed.length > 0,
    staleTime: 30_000,
  });
}
