import { useMutation, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import {
  type CreatedLink,
  createLink,
  type CreateLinkRequest,
  deleteLink,
  favoriteLink,
  type LinkListResponse,
  type LinkSummary,
  toggleLink,
} from "../api/shortlink";
import { copyToClipboard } from "../lib/clipboard";
import { shortlinkKeys } from "../queryKeys";

/** 낙관적 행의 슬러그. 서버 응답이 오면 통째로 갈린다. */
const PENDING_SLUG = "…";

/**
 * 발급. <b>화면에는 낙관적으로 넣고, 클립보드에는 서버 응답만 넣는다</b>(명세 §4.1).
 *
 * <p>둘을 갈라 두는 이유가 이 훅의 전부다. 목록에 한 줄 먼저 보이는 것은 되돌리면 그만이지만,
 * 클립보드에 잘못된 주소가 들어가면 사용자는 그것을 <b>남에게 보낸 뒤에야</b> 알게 된다.
 * 그래서 슬러그를 미리 지어내지 않고 `…`로 둔다.
 */
export function useCreateLink() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: CreateLinkRequest) => createLink(body),
    onMutate: async (body) => {
      await queryClient.cancelQueries({ queryKey: shortlinkKeys.lists });
      const snapshot = queryClient.getQueriesData<LinkListResponse>({
        queryKey: shortlinkKeys.lists,
      });
      queryClient.setQueriesData<LinkListResponse>(
        { queryKey: shortlinkKeys.lists },
        (previous) =>
          previous && {
            ...previous,
            counts: { ...previous.counts, all: previous.counts.all + 1 },
            recent: [pendingRow(body), ...previous.recent],
          },
      );
      return { snapshot };
    },
    onError: (_error, _body, context) => {
      // 실패하면 낙관적 행을 걷어낸다 — 없는 링크가 목록에 남아 있으면 다음에 눌러 보고 404다.
      context?.snapshot.forEach(([key, data]) =>
        queryClient.setQueryData(key, data),
      );
      toast("링크를 만들지 못했어요.", "error");
    },
    onSuccess: async (created: CreatedLink) => {
      const copied = await copyToClipboard(created.shortUrl);
      toast(
        copied ? "복사했어요" : "만들었어요 — 복사는 직접 해 주세요.",
        copied ? "success" : "info",
      );
    },
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useToggleLink() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (slug: string) => toggleLink(slug),
    onError: () => toast("상태를 바꾸지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useFavoriteLink() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (slug: string) => favoriteLink(slug),
    onError: () => toast("즐겨찾기를 바꾸지 못했어요.", "error"),
    onSettled: () => invalidateAll(queryClient),
  });
}

export function useDeleteLink() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (slug: string) => deleteLink(slug),
    onError: () => toast("삭제하지 못했어요.", "error"),
    onSuccess: () => toast("삭제했어요"),
    onSettled: () => invalidateAll(queryClient),
  });
}

/** 목록 화면이 쓰는 뮤테이션 묶음. 네 개를 따로 부르면 화면이 훅 이름만 네 줄 늘어난다. */
export function useLinkMutations() {
  return {
    create: useCreateLink(),
    toggle: useToggleLink(),
    favorite: useFavoriteLink(),
    remove: useDeleteLink(),
  };
}

/**
 * 목록·요약·태그를 함께 무효화한다. 발급 하나가 사이드바 개수와 `/select` 카드까지
 * 바꾸므로, 셋을 따로 갱신하면 화면마다 다른 숫자가 남는다.
 */
function invalidateAll(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: shortlinkKeys.all });
}

function pendingRow(body: CreateLinkRequest): LinkSummary {
  return {
    slug: body.slug?.trim() || PENDING_SLUG,
    // 낙관적 행에는 주소를 짓지 않는다. 화면은 슬러그 자리만 흐리게 보여 준다.
    shortUrl: "",
    targetUrl: body.targetUrl,
    memo: body.memo ?? null,
    tags: body.tags ?? [],
    custom: Boolean(body.slug),
    favorite: false,
    state: "ACTIVE",
    hasPassword: Boolean(body.password),
    visitCount: 0,
    lastVisitedAt: null,
  };
}
