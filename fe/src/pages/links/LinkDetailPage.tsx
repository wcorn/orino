import { ArrowLeft } from "lucide-react";
import { Link, useParams } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";

/**
 * 링크 상세 `/links/:slug`.
 *
 * <p><b>경로 키가 id가 아니라 slug다</b> — 슬러그는 불변이고 사용자가 실제로 보고 부르는
 * 식별자다(결정 기록 D-5). 통계 · QR · 목적지 교체 이력은 <b>#1242</b>에서 붙는다.
 */
export function LinkDetailPage() {
  const { slug = "" } = useParams();

  return (
    <div className="mx-auto flex max-w-[820px] flex-col gap-5">
      <Link
        to="/links"
        className="text-muted-foreground hover:text-foreground flex items-center gap-1 text-[13px]"
      >
        <ArrowLeft className="size-3.5" />
        링크 목록
      </Link>
      <PageHeader title={slug} />
      <p className="text-muted-foreground text-sm">
        통계 · QR · 목적지 교체 이력은 다음 작업(#1242)에서 붙습니다.
      </p>
    </div>
  );
}
