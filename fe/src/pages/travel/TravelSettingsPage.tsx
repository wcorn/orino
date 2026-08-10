import { Bell, Send } from "lucide-react";
import { useId, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { LoadingText } from "@/components/ui/loading-text";
import { Select } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { sendTestPush } from "@/features/travel/api/push";
import { useTravelSummary } from "@/features/travel/hooks/useTravelSummary";
import { useTrip } from "@/features/travel/hooks/useTrip";
import { useUpdateTrip } from "@/features/travel/hooks/useTripMutations";
import { NOTIFY_MINUTES_OPTIONS } from "@/features/travel/lib/destinations";
import { OfflineSection } from "@/features/travel/offline/OfflineSection";
import { usePushSubscription } from "@/features/travel/push/usePushSubscription";
import { toast } from "@/shared/lib/toast";

/** 권한 행의 상태 표시. 지금 무엇을 할 수 있는지가 한눈에 보여야 한다. */
const PERMISSION_LABEL = {
  loading: { text: "확인 중", variant: "secondary" as const },
  unsupported: { text: "지원 안 함", variant: "secondary" as const },
  unavailable: { text: "준비 중", variant: "secondary" as const },
  idle: { text: "요청 필요", variant: "warning" as const },
  subscribed: { text: "허용됨", variant: "success" as const },
  denied: { text: "차단됨", variant: "destructive" as const },
};

/**
 * S-09 설정.
 *
 * <p>알림 설정은 <b>여행 단위</b>다(기본 알림 시점·아침 요약). 그래서 어느 여행을 고를지가
 * 문제인데, 지금 관심 있는 여행은 하나뿐이다 — 진행 중이거나 다음 예정 여행. 그 여행의
 * 설정을 보여준다.
 *
 */
export function TravelSettingsPage() {
  const { data: summary, isPending } = useTravelSummary();
  const push = usePushSubscription();
  const [testing, setTesting] = useState(false);

  const tripId = summary?.ongoing?.id ?? summary?.next?.id ?? null;
  const { data: trip } = useTrip(tripId);
  const updateTrip = useUpdateTrip(tripId ?? 0);

  const notifyId = useId();
  const permission = PERMISSION_LABEL[push.state];

  const saveSettings = async (
    defaultNotifyMinutes: number,
    morningSummaryEnabled: boolean,
  ) => {
    if (!trip) return;
    try {
      // 구간을 보내지 않는다 — 알림 설정만 바꾸는 요청이 날짜별 기준 도시를 되감으면 안 된다.
      await updateTrip.mutateAsync({
        title: trip.title,
        startDate: trip.startDate,
        endDate: trip.endDate,
        defaultNotifyMinutes,
        morningSummaryEnabled,
      });
      toast("설정을 저장했어요.", "success");
    } catch {
      toast("저장하지 못했어요.", "error");
    }
  };

  const requestPermission = async () => {
    try {
      if (await push.subscribe()) {
        toast("알림을 켰어요.", "success");
      } else {
        toast("알림 권한이 필요해요.", "error");
      }
    } catch {
      toast("알림을 켜지 못했어요.", "error");
    }
  };

  const sendTest = async () => {
    setTesting(true);
    try {
      const delivered = await sendTestPush();
      toast(
        delivered > 0
          ? `${delivered}개 기기로 보냈어요.`
          : "보낼 기기가 없어요. 먼저 알림을 켜 주세요.",
        delivered > 0 ? "success" : "error",
      );
    } catch {
      toast("보내지 못했어요.", "error");
    } finally {
      setTesting(false);
    }
  };

  if (isPending) {
    return (
      <div className="grid min-h-[40svh] place-items-center">
        <LoadingText />
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-[520px] flex-col gap-5 px-4 pt-3">
      <h1 className="text-heading font-semibold">여행 설정</h1>

      <section className="flex flex-col gap-3">
        <h2 className="text-caption text-muted-foreground font-semibold">
          알림
        </h2>

        <div className="flex items-center gap-3">
          <div className="min-w-0 flex-1">
            <p className="flex items-center gap-1.5 text-sm font-medium">
              <Bell className="size-3.5" />
              알림 권한
            </p>
            <p className="text-muted-foreground text-xs">
              서버 푸시로 일정·출발 알림을 보냅니다
            </p>
          </div>
          <Badge variant={permission.variant}>{permission.text}</Badge>
          {push.state === "idle" && (
            <Button
              variant="outline"
              size="sm"
              disabled={push.pending}
              onClick={() => void requestPermission()}
            >
              권한 요청
            </Button>
          )}
          {push.state === "subscribed" && (
            <Button
              variant="ghost"
              size="sm"
              disabled={push.pending}
              onClick={() => void push.unsubscribe()}
            >
              끄기
            </Button>
          )}
        </div>

        {push.state === "denied" && (
          // 브라우저가 막은 것이라 앱에서 다시 물어볼 수 없다.
          <p className="text-muted-foreground text-xs">
            브라우저 설정에서 이 사이트의 알림을 허용해 주세요.
          </p>
        )}

        {push.state === "subscribed" && (
          <Button
            variant="outline"
            size="sm"
            className="w-full"
            disabled={testing}
            onClick={() => void sendTest()}
          >
            <Send className="size-3.5" />
            테스트 알림 보내기
          </Button>
        )}

        {trip ? (
          <>
            <FormField label="기본 알림 시점" labelId={notifyId}>
              <Select
                value={String(trip.defaultNotifyMinutes)}
                onValueChange={(value) =>
                  void saveSettings(Number(value), trip.morningSummaryEnabled)
                }
                options={[...NOTIFY_MINUTES_OPTIONS]}
                ariaLabelledby={notifyId}
              />
            </FormField>

            <div className="flex items-center gap-3">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium">아침 요약 알림</p>
                <p className="text-muted-foreground text-xs">
                  여행 기간 중 매일 현지 08:00
                </p>
              </div>
              <Switch
                checked={trip.morningSummaryEnabled}
                onCheckedChange={(checked) =>
                  void saveSettings(trip.defaultNotifyMinutes, checked)
                }
                aria-label="아침 요약 알림"
              />
            </div>
            <p className="text-muted-foreground text-xs">
              {trip.title}에 적용됩니다
            </p>
          </>
        ) : (
          // 알림 설정은 여행에 붙는 값이라 여행이 없으면 보여줄 것도 없다.
          <p className="text-muted-foreground text-sm">
            여행을 만들면 알림 설정을 할 수 있어요.
          </p>
        )}
      </section>

      <OfflineSection />

      <section className="flex flex-col gap-2 border-t pt-5">
        <h2 className="text-caption text-muted-foreground font-semibold">
          정보
        </h2>
        <p className="text-muted-foreground text-xs">버전 {__APP_VERSION__}</p>
        <p className="text-muted-foreground text-xs">
          장소 Google Places · 지도 © OpenStreetMap
        </p>
      </section>
    </div>
  );
}
