import { useQuery } from "@tanstack/react-query";

import { useDebouncedValue } from "@/shared/lib/useDebouncedValue";

import { fetchOgPreview } from "../api/shortlink";
import { shortlinkKeys } from "../queryKeys";

/** 타이핑이 멈춘 뒤에 긁는다. 글자마다 부르면 남의 서버를 두드리는 횟수가 URL 길이만큼이 된다. */
const DEBOUNCE_MS = 600;

/**
 * 목적지 OG 프리뷰.
 *
 * <p><b>제출을 막지 않는다</b>(명세 §4.4). 이 훅이 무엇을 돌려주든 「만들기」는 눌린다 —
 * 실패·지연은 미리보기 자리가 비는 것으로만 보인다. 그래서 재시도도 하지 않는다.
 */
export function useOgPreview(url: string) {
  const debounced = useDebouncedValue(url.trim(), DEBOUNCE_MS);
  const fetchable = /^https?:\/\/\S+\.\S+/.test(debounced);

  const query = useQuery({
    queryKey: [...shortlinkKeys.all, "og-preview", debounced],
    queryFn: () => fetchOgPreview(debounced),
    enabled: fetchable,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });

  return {
    preview: query.data?.ok ? query.data : null,
    loading: fetchable && query.isFetching,
  };
}
