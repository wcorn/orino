import { PageHeader } from "@/components/PageHeader";

/**
 * 링크 목록 `/links`.
 *
 * <p>이 변경(#1238)은 <b>진입 동선까지만</b> 만든다 — `/select` 3카드와 사이드바 3분할이
 * 갈 곳이 있어야 하므로 라우트와 껍데기를 먼저 세운다. 빠른 발급 바 · 필터 · 카드 행은
 * <b>#1239</b>에서 이 자리에 붙는다.
 *
 * <p>화면 설계 §3.2의 본문 래퍼(`max-w-[720px]`)와 제목·설명은 여기서 확정한다 —
 * 다음 변경이 레이아웃부터 다시 정하지 않도록.
 */
export function LinkListPage() {
  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-5">
      <PageHeader
        title="링크"
        description="s.orino.dev · 자동 발급 5자, 최종 17자"
      />
      <p className="text-muted-foreground text-sm">
        목록과 빠른 발급은 다음 작업(#1239)에서 붙습니다.
      </p>
    </div>
  );
}
