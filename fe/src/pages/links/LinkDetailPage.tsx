import { ArrowLeft, Globe, SquarePen } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";

import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import type { LinkState } from "@/features/shortlink/api/shortlink";
import { QrPanel } from "@/features/shortlink/components/QrPanel";
import { ShortUrlText } from "@/features/shortlink/components/ShortUrlText";
import { StatsPanel } from "@/features/shortlink/components/StatsPanel";
import { TargetEditModal } from "@/features/shortlink/components/TargetEditModal";
import { TargetHistoryList } from "@/features/shortlink/components/TargetHistoryList";
import {
  useLink,
  useLinkStats,
  useUpdateLink,
} from "@/features/shortlink/hooks/useLink";
import { copyToClipboard } from "@/features/shortlink/lib/clipboard";
import { toast } from "@/shared/lib/toast";

const STATE_BADGE: Record<
  LinkState,
  { label: string; variant: "success" | "secondary" }
> = {
  ACTIVE: { label: "활성", variant: "success" },
  DISABLED: { label: "꺼짐", variant: "secondary" },
  EXPIRED: { label: "만료", variant: "secondary" },
};

/**
 * 링크 상세 `/links/{slug}`.
 *
 * <p><b>경로 키가 id가 아니라 slug다</b> — 슬러그는 불변이고 사용자가 실제로 보고 부르는
 * 식별자다(결정 기록 D-5).
 *
 * <p>이 화면의 중심은 통계가 아니라 <b>목적지 교체</b>다. UC-01(죽은 링크 되살리기)이 끝나는
 * 자리이고, 그래서 통계 조회가 실패해도 주소·목적지·이력은 그대로 뜬다.
 */
export function LinkDetailPage() {
  const { slug = "" } = useParams();
  const { data: link, isPending, isError } = useLink(slug);
  const { data: stats } = useLinkStats(slug);
  const update = useUpdateLink(slug);
  const [editOpen, setEditOpen] = useState(false);

  if (isPending) {
    return <LoadingText />;
  }
  if (isError || !link) {
    return (
      <div className="mx-auto max-w-[820px]">
        <Alert variant="destructive">링크를 불러오지 못했어요.</Alert>
      </div>
    );
  }

  const badge = STATE_BADGE[link.state];

  const copy = async () => {
    const copied = await copyToClipboard(link.shortUrl);
    toast(
      copied ? "복사했어요" : "복사하지 못했어요.",
      copied ? "success" : "error",
    );
  };

  return (
    <div className="mx-auto flex max-w-[820px] flex-col gap-5">
      <Link
        to="/links"
        className="text-muted-foreground hover:text-foreground flex w-fit items-center gap-1 text-[13px]"
      >
        <ArrowLeft className="size-3.5" />
        링크 목록
      </Link>

      <section className="bg-card ring-foreground/10 flex flex-wrap gap-5 rounded-xl p-5 ring-1">
        <div className="flex min-w-0 flex-1 flex-col gap-3">
          <div className="flex items-center gap-2">
            <Badge variant={badge.variant}>{badge.label}</Badge>
            <span className="text-muted-foreground text-xs">
              {formatDate(link.createdAt)} 발급
              {link.tags.length > 0 ? ` · ${link.tags.join(" · ")}` : ""}
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <ShortUrlText
              shortUrl={link.shortUrl}
              slug={link.slug}
              className="text-[28px] tracking-[-0.02em]"
            />
            <Button type="button" variant="secondary" size="sm" onClick={copy}>
              복사
            </Button>
          </div>

          <div className="bg-muted flex flex-col gap-2 rounded-lg p-3">
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground flex min-w-0 items-center gap-2 text-[13px]">
                <Globe className="size-3.5 shrink-0" />
                <span className="truncate">{link.targetUrl}</span>
              </span>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setEditOpen(true)}
              >
                <SquarePen className="size-3.5" />
                목적지 수정
              </Button>
            </div>
            <p className="text-muted-foreground text-xs">
              주소는 그대로 두고 목적지만 갈아끼웁니다. 이미 뿌린 링크가 전부
              살아납니다.
            </p>
          </div>

          {link.memo && <p className="text-[13px]">{link.memo}</p>}
        </div>

        {/* 꺼진 링크의 QR은 내보내지 않는다 — 목록 행과 같은 규칙이다. */}
        {link.state === "ACTIVE" && (
          <QrPanel
            value={link.shortUrl}
            slug={link.slug}
            size={140}
            saveLabel="PNG 저장"
          />
        )}
      </section>

      {stats && <StatsPanel stats={stats} />}

      <TargetHistoryList history={link.targetHistory} />

      <TargetEditModal
        open={editOpen}
        onOpenChange={setEditOpen}
        currentTargetUrl={link.targetUrl}
        pending={update.isPending}
        onSubmit={(targetUrl, reason) =>
          update.mutate(
            {
              targetUrl,
              targetChangeReason: reason || undefined,
            },
            { onSuccess: () => setEditOpen(false) },
          )
        }
      />
    </div>
  );
}

function formatDate(isoDateTime: string): string {
  const date = new Date(isoDateTime);
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}.${month}.${day}`;
}
