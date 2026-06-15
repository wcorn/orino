import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

import { useDisconnectGoogle } from "../hooks/useDisconnectGoogle";
import { useGoogleStatus } from "../hooks/useGoogleStatus";
import { GoogleConnectButton } from "./GoogleConnectButton";

function formatDate(iso: string | null): string {
  if (!iso) return "-";
  const date = new Date(iso);
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`;
}

/** Google 연동 상태 카드 (연동 설정 화면). 연결됨: 계정/연결일 + 해제 / 미연동: 연결 CTA. */
export function GoogleConnectionCard() {
  const { data: status, isLoading } = useGoogleStatus();
  const disconnect = useDisconnectGoogle();

  return (
    <Card>
      <CardHeader>
        <CardTitle>Google 연동</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {isLoading ? (
          <p className="text-muted-foreground text-sm">불러오는 중…</p>
        ) : status?.connected ? (
          <>
            <dl className="grid grid-cols-[5rem_1fr] gap-y-1 text-sm">
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
