import { AlertTriangle } from "lucide-react";

import { GoogleConnectButton } from "@/features/google/components/GoogleConnectButton";

import type { PlannerCalendarFeed } from "../../api/feed";

interface Props {
  feed?: PlannerCalendarFeed;
}

/**
 * 통합 캘린더의 연동/오류 상태 배너 — 피드 한 소스로 미연동/재연동/부분실패를 일원화한다.
 *
 * - 미연동(connected=false, google 에러 없음): 연결 CTA
 * - 재연동 필요(connected=false, google 에러 있음 = invalid_grant): 다시 연결 CTA
 * - 부분 실패(connected=true, partial): 경고만
 */
export function PlannerConnectionBanner({ feed }: Props) {
  if (!feed) {
    return null;
  }

  const hasGoogleError = feed.errors.some((e) => e.source.startsWith("google"));

  if (!feed.googleConnected) {
    return hasGoogleError ? (
      <Banner message="Google 연동이 만료되었습니다." action="다시 연결" />
    ) : (
      <Banner
        message="Google 캘린더가 연결되지 않았습니다."
        action="Google 연결"
      />
    );
  }

  if (feed.partial) {
    return <Banner message="일부 일정을 불러오지 못했습니다." />;
  }

  return null;
}

interface BannerProps {
  message: string;
  action?: string;
}

function Banner({ message, action }: BannerProps) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm dark:border-amber-900/60 dark:bg-amber-950/40"
    >
      <span className="flex items-center gap-2 text-amber-700 dark:text-amber-300">
        <AlertTriangle className="size-4" />
        {message}
      </span>
      {action && (
        <GoogleConnectButton variant="outline" size="sm">
          {action}
        </GoogleConnectButton>
      )}
    </div>
  );
}
