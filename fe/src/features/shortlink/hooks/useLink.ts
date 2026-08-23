import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import {
  fetchLink,
  fetchLinkStats,
  updateLink,
  type UpdateLinkRequest,
} from "../api/shortlink";
import { shortlinkKeys } from "../queryKeys";

/** 링크 상세. 목적지 교체 이력이 함께 온다 — 이력이 빈 링크는 없다(최초 발급이 첫 줄). */
export function useLink(slug: string) {
  return useQuery({
    queryKey: shortlinkKeys.link(slug),
    queryFn: () => fetchLink(slug),
    enabled: slug !== "",
  });
}

/**
 * 방문 통계. <b>상세와 따로 부른다</b> — 통계가 느리거나 실패해도 주소·목적지·이력은 떠야 한다.
 * 이 화면의 본체는 통계가 아니라 "이 주소가 지금 어디를 가리키는가"다.
 */
export function useLinkStats(slug: string, range = "30d") {
  return useQuery({
    queryKey: [...shortlinkKeys.link(slug), "stats", range],
    queryFn: () => fetchLinkStats(slug, range),
    enabled: slug !== "",
  });
}

/**
 * 편집. 목적지를 갈아끼우면 <b>주소는 그대로 두고</b> 이력이 한 줄 는다 —
 * 이미 뿌린 링크가 전부 살아나는 지점이다(명세 §5.1).
 */
export function useUpdateLink(slug: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: UpdateLinkRequest) => updateLink(slug, body),
    onSuccess: (detail) => {
      // 응답이 곧 최신 상세다. 다시 불러오지 않고 캐시를 갈아끼운다.
      queryClient.setQueryData(shortlinkKeys.link(slug), detail);
      // 목록의 목적지·메모도 함께 바뀐다.
      void queryClient.invalidateQueries({ queryKey: shortlinkKeys.lists });
      toast("바꿨어요", "success");
    },
    onError: () => toast("바꾸지 못했어요.", "error"),
  });
}
