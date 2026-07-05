import { useQuery } from "@tanstack/react-query";

import { fetchMemoTree } from "../api/memos";
import { memoKeys } from "../queryKeys";

export function useMemoTree() {
  return useQuery({
    queryKey: memoKeys.tree(),
    queryFn: fetchMemoTree,
  });
}
