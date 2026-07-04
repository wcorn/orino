import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { LoadingText } from "@/components/ui/loading-text";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";

import { useDisconnectGoogle } from "../hooks/useDisconnectGoogle";
import { useGoogleStatus } from "../hooks/useGoogleStatus";
import { useReviewMirrorToggle } from "../hooks/useReviewMirrorToggle";
import { GoogleConnectButton } from "./GoogleConnectButton";

const REVIEW_MIRROR_LABEL = "복습 일정을 Google 캘린더에 표시";

function formatDate(iso: string | null): string {
  if (!iso) return "-";
  const date = new Date(iso);
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`;
}

/** Google 연동 상태 카드 (연동 설정 화면). 연결됨: 계정/연결일 + 해제 / 미연동: 연결 CTA. */
export function GoogleConnectionCard() {
  const { data: status, isLoading } = useGoogleStatus();
  const disconnect = useDisconnectGoogle();
  const mirror = useReviewMirrorToggle();

  return (
    <Card>
      <CardHeader>
        <CardTitle>Google 연동</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {isLoading ? (
          <LoadingText />
        ) : status?.connected ? (
          <>
            <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm sm:grid-cols-[5rem_1fr]">
              <dt className="text-muted-foreground">상태</dt>
              <dd>연결됨</dd>
              {status.googleEmail && (
                <>
                  <dt className="text-muted-foreground">계정</dt>
                  <dd>{status.googleEmail}</dd>
                </>
              )}
              <dt className="text-muted-foreground">연결일</dt>
              <dd>{formatDate(status.connectedAt)}</dd>
            </dl>
            <div className="flex items-start justify-between gap-3 border-t pt-3">
              <div className="flex flex-col gap-0.5">
                <span className="text-sm font-medium">
                  {REVIEW_MIRROR_LABEL}
                </span>
                <p className="text-muted-foreground text-xs">
                  복습 일정을 보조 캘린더 &ldquo;orino 복습&rdquo;에 단방향으로
                  표시합니다. orino가 원본이며 Google에는 사본만 올라갑니다.
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                {/* 토글 중 표기는 항상 렌더하고 opacity로만 숨겨, 나타날 때 Switch가 밀리지 않게 공간을 예약한다. */}
                <span
                  aria-hidden={!mirror.isPending}
                  className={cn(
                    "text-muted-foreground text-xs transition-opacity",
                    !mirror.isPending && "opacity-0",
                  )}
                >
                  {status.reviewMirrorEnabled ? "끄는 중…" : "켜는 중…"}
                </span>
                <Switch
                  checked={status.reviewMirrorEnabled}
                  disabled={mirror.isPending}
                  onCheckedChange={(next) => mirror.mutate(next)}
                  aria-label={REVIEW_MIRROR_LABEL}
                />
              </div>
            </div>
            <div>
              <Button
                variant="outline"
                size="sm"
                disabled={disconnect.isPending}
                onClick={() => disconnect.mutate()}
              >
                {disconnect.isPending ? "해제 중…" : "연결 해제"}
              </Button>
            </div>
          </>
        ) : (
          <>
            <p className="text-muted-foreground text-sm">연결되지 않음</p>
            <div>
              <GoogleConnectButton />
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
