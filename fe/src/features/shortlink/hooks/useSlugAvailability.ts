import { useQuery } from "@tanstack/react-query";

import { useDebouncedValue } from "@/shared/lib/useDebouncedValue";

import { checkSlugAvailable } from "../api/shortlink";
import { shortlinkKeys } from "../queryKeys";

/** 타이핑이 멈춘 뒤 물어본다. 글자마다 부르면 한 슬러그에 요청이 열 번 난다. */
const DEBOUNCE_MS = 300;

/**
 * 커스텀 슬러그 중복 검사.
 *
 * <p>비어 있으면 묻지 않는다 — 비운 채로 두면 서버가 자동으로 5자를 뽑는 것이 기본 경로다.
 * 문자셋을 벗어난 슬러그에는 서버가 400(`SL-ERR-004`)으로 답하므로, 화면은 "사용 중"과
 * "쓸 수 없는 글자"를 나눠 보여 줄 수 있다.
 */
export function useSlugAvailability(slug: string) {
  const debounced = useDebouncedValue(slug.trim(), DEBOUNCE_MS);

  const query = useQuery({
    queryKey: [...shortlinkKeys.all, "slug-available", debounced],
    queryFn: () => checkSlugAvailable(debounced),
    enabled: debounced.length > 0,
    retry: false,
    staleTime: 30 * 1000,
  });

  return {
    /** 검사가 끝났고 이미 쓰이는 슬러그일 때만 true. */
    taken: query.data === false,
    /** 서버가 거절한 슬러그(문자셋·길이). */
    invalid: query.isError,
    checking: query.isFetching,
    /** 디바운스가 끝나 실제로 물어본 값. 화면이 미리보기에 쓴다. */
    checkedSlug: debounced,
  };
}
