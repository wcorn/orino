import { Copy, Ellipsis, QrCode, Star } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Menu, MenuItem } from "@/components/ui/menu";
import { cn } from "@/lib/utils";
import { toast } from "@/shared/lib/toast";

import type { LinkSummary } from "../api/shortlink";
import { copyToClipboard } from "../lib/clipboard";
import { ShortUrlText } from "./ShortUrlText";

interface LinkRowProps {
  link: LinkSummary;
  /**
   * 좁은 화면. 방문 수 칸과 QR을 접어 <b>주소 · 목적지 두 줄</b>로 줄인다(화면 설계 §6).
   * QR은 상세 화면에 그대로 있다.
   */
  compact?: boolean;
  onShowQr: (link: LinkSummary) => void;
  onToggle: (link: LinkSummary) => void;
  onFavorite: (link: LinkSummary) => void;
  onDelete: (link: LinkSummary) => void;
}

const STATE_BADGE: Record<string, string> = {
  DISABLED: "꺼짐",
  EXPIRED: "만료",
};

/**
 * 목록 카드 한 행. 행 전체가 상세로 가는 버튼이고, 안쪽 액션은 이동을 막는다.
 *
 * <p>비활성·만료 행에는 <b>QR 버튼을 두지 않는다</b>(화면 설계 §3.3). 지금 열리지 않는
 * 주소의 QR을 내보내면, 그걸 인쇄해 붙이는 순간 아무 데도 닿지 않는 종이가 된다.
 */
export function LinkRow({
  link,
  compact = false,
  onShowQr,
  onToggle,
  onFavorite,
  onDelete,
}: LinkRowProps) {
  const navigate = useNavigate();
  const inactive = link.state !== "ACTIVE";
  // 낙관적 행은 아직 주소가 없다. 누르면 없는 상세로 가므로 이동·액션을 막는다.
  const pending = link.shortUrl === "";

  const copy = async (event: React.MouseEvent) => {
    // 복사는 복사만 한다 — 행 이동이 함께 일어나면 사용자는 목록을 잃는다.
    event.stopPropagation();
    const copied = await copyToClipboard(link.shortUrl);
    toast(
      copied ? "복사했어요" : "복사하지 못했어요.",
      copied ? "success" : "error",
    );
  };

  return (
    <div
      role={pending ? undefined : "button"}
      tabIndex={pending ? undefined : 0}
      onClick={() => !pending && navigate(`/links/${link.slug}`)}
      onKeyDown={(event) => {
        if (!pending && (event.key === "Enter" || event.key === " ")) {
          event.preventDefault();
          navigate(`/links/${link.slug}`);
        }
      }}
      className={cn(
        "bg-card ring-foreground/10 flex items-center gap-3 rounded-xl p-[14px_16px] ring-1 transition-all",
        !pending && "hover:ring-primary cursor-pointer",
        inactive && "opacity-[.62]",
        pending && "animate-pulse",
      )}
    >
      <div className="flex min-w-0 flex-1 flex-col gap-[3px]">
        <div className="flex items-center gap-1.5">
          {link.favorite && (
            <Star
              aria-label="즐겨찾기"
              className="text-primary size-3.5 shrink-0 fill-current"
            />
          )}
          <ShortUrlText
            shortUrl={link.shortUrl}
            slug={link.slug}
            className="text-[15px]"
          />
          {link.custom && <Badge variant="secondary">커스텀</Badge>}
          {inactive && (
            <Badge variant="secondary">{STATE_BADGE[link.state]}</Badge>
          )}
          {!pending && (
            <Button
              type="button"
              variant="ghost"
              size="icon-xs"
              aria-label="주소 복사"
              onClick={copy}
            >
              <Copy className="size-3" />
            </Button>
          )}
        </div>
        <p className="text-muted-foreground truncate text-[13px]">
          {link.targetUrl}
        </p>
        {/* 좁은 화면에서는 메모·태그 줄을 접는다 — 두 줄 안에 주소와 목적지가 들어와야 한다. */}
        {!compact && (link.memo || link.tags.length > 0) && (
          <p className="text-muted-foreground flex items-center gap-1.5 text-xs">
            {link.memo}
            {link.tags.map((tag) => (
              <Badge key={tag} variant="outline" className="text-xs">
                {tag}
              </Badge>
            ))}
          </p>
        )}
      </div>

      {!compact && (
        <div className="flex w-24 flex-none flex-col items-end">
          <span className="text-[15px] font-semibold tabular-nums">
            {link.visitCount}
          </span>
          <span className="text-muted-foreground text-xs">
            {formatLastVisit(link.lastVisitedAt)}
          </span>
        </div>
      )}

      {!pending && (
        <div
          className="flex flex-none items-center gap-1"
          onClick={(event) => event.stopPropagation()}
        >
          {/* 지금 열리지 않는 주소의 QR은 내보내지 않는다. 좁은 화면에서는 상세로 미룬다. */}
          {!inactive && !compact && (
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              aria-label="QR 보기"
              onClick={() => onShowQr(link)}
            >
              <QrCode className="size-4" />
            </Button>
          )}
          <Menu
            trigger={
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label="더보기"
              >
                <Ellipsis className="size-4" />
              </Button>
            }
          >
            <MenuItem onClick={() => navigate(`/links/${link.slug}`)}>
              상세 · 목적지 수정
            </MenuItem>
            <MenuItem onClick={() => onFavorite(link)}>
              {link.favorite ? "즐겨찾기 해제" : "즐겨찾기"}
            </MenuItem>
            <MenuItem onClick={() => onToggle(link)}>
              {link.state === "DISABLED" ? "활성화" : "비활성화"}
            </MenuItem>
            <MenuItem variant="destructive" onClick={() => onDelete(link)}>
              삭제
            </MenuItem>
          </Menu>
        </div>
      )}
    </div>
  );
}

/** 마지막 방문. 통계(#1240) 전까지는 항상 비어 있어 `—`로 보인다. */
function formatLastVisit(lastVisitedAt: string | null): string {
  if (!lastVisitedAt) {
    return "—";
  }
  const date = new Date(lastVisitedAt);
  return `${date.getMonth() + 1}.${date.getDate()}`;
}
