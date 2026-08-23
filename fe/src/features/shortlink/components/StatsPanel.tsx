import { Bot, Globe, Monitor, Smartphone, Tablet } from "lucide-react";

import { cn } from "@/lib/utils";

import type { LinkStats } from "../api/shortlink";

interface StatsPanelProps {
  stats: LinkStats;
}

const DEVICE_LABEL: Record<string, string> = {
  MOBILE: "모바일",
  DESKTOP: "데스크탑",
  TABLET: "태블릿",
  UNKNOWN: "알 수 없음",
};

const DEVICE_ICON: Record<string, typeof Smartphone> = {
  MOBILE: Smartphone,
  DESKTOP: Monitor,
  TABLET: Tablet,
  UNKNOWN: Globe,
};

/**
 * 통계 카드(화면 설계 §5).
 *
 * <p><b>봇 수를 총 방문에 더하지 않는다.</b> 카카오톡·슬랙에 링크를 붙이면 사람이 누르기 전에
 * 프리뷰 봇이 먼저 열고, 그걸 같이 세면 방문 수가 실제의 몇 배가 된다(명세 §8.2).
 * 그리고 <b>봇 판정이 완벽하지 않다는 것을 화면에 그대로 적는다</b> — 통계는 참고치다.
 */
export function StatsPanel({ stats }: StatsPanelProps) {
  const max = Math.max(1, ...stats.daily.map((day) => day.count));
  const axis = axisLabels(stats.daily);

  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-4 rounded-xl p-5 ring-1">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex gap-8">
          <Metric label="총 방문" value={stats.totalVisits} />
          <Metric label="최근 7일" value={stats.last7Days} />
          <Metric label="마지막 방문" text={formatDate(stats.lastVisitedAt)} />
        </div>
        <p className="text-muted-foreground flex items-center gap-1.5 text-xs">
          <Bot className="size-3.5" />
          봇·프리뷰 {stats.botVisits}건은 따로 셉니다 — 통계는 참고치
        </p>
      </div>

      {/* 최근 N일 일별 막대. 값이 없는 날도 칸을 차지한다 — 빈 날은 그래프의 구멍이 아니다. */}
      <div>
        <div className="flex h-[120px] items-end gap-[3px]">
          {stats.daily.map((day) => (
            <div
              key={day.date}
              title={`${day.date} · ${day.count}`}
              className={cn(
                "flex-1 rounded-t-[2px]",
                day.count > 0 ? "bg-primary" : "bg-muted",
              )}
              style={{
                height:
                  day.count > 0
                    ? `${Math.max(4, (day.count / max) * 100)}%`
                    : "4px",
              }}
            />
          ))}
        </div>
        <div className="text-muted-foreground mt-1.5 flex justify-between text-xs">
          {axis.map((label, index) => (
            <span key={`${label}-${index}`}>{label}</span>
          ))}
        </div>
      </div>

      {stats.referrers.length > 0 && (
        <div className="flex flex-col gap-2">
          <h3 className="text-caption text-muted-foreground font-semibold">
            유입 경로
          </h3>
          {stats.referrers.map((referrer) => (
            <div key={referrer.domain} className="flex flex-col gap-1">
              <div className="flex items-center justify-between text-[13px]">
                <span className="truncate">{referrer.domain}</span>
                <span className="tabular-nums">{referrer.count}</span>
              </div>
              <div className="bg-muted h-1.5 rounded-full">
                <div
                  className="bg-primary h-1.5 rounded-full"
                  style={{
                    width: `${share(referrer.count, stats.referrers)}%`,
                  }}
                />
              </div>
            </div>
          ))}
        </div>
      )}

      {(stats.devices.length > 0 || stats.countries.length > 0) && (
        <div className="flex flex-col gap-2 border-t pt-3">
          {stats.devices.map((device) => {
            const Icon = DEVICE_ICON[device.device] ?? Globe;
            return (
              <div
                key={device.device}
                className="flex items-center justify-between text-[13px]"
              >
                <span className="text-muted-foreground flex items-center gap-1.5">
                  <Icon className="size-3.5" />
                  {DEVICE_LABEL[device.device] ?? device.device}
                </span>
                <span className="tabular-nums">
                  {Math.round(device.ratio * 100)}%
                </span>
              </div>
            );
          })}
          {stats.countries.map((country) => (
            <div
              key={country.country}
              className="flex items-center justify-between text-[13px]"
            >
              <span className="text-muted-foreground flex items-center gap-1.5">
                <Globe className="size-3.5" />
                {country.country}
              </span>
              <span className="tabular-nums">
                {Math.round(country.ratio * 100)}%
              </span>
            </div>
          ))}
          <p className="text-muted-foreground text-xs">
            국가만 봅니다. IP는 저장하지 않아요.
          </p>
        </div>
      )}
    </section>
  );
}

function Metric({
  label,
  value,
  text,
}: {
  label: string;
  value?: number;
  text?: string;
}) {
  return (
    <div>
      <p className="text-[28px] font-semibold tabular-nums">
        {value !== undefined ? value : text}
      </p>
      <p className="text-muted-foreground text-xs">{label}</p>
    </div>
  );
}

/** 축 라벨 3개 — 시작일 · 중간 · 오늘. */
function axisLabels(daily: { date: string }[]): string[] {
  if (daily.length === 0) {
    return [];
  }
  const middle = daily[Math.floor(daily.length / 2)];
  return [
    shortDate(daily[0].date),
    shortDate(middle.date),
    // 마지막 칸은 언제나 오늘이다(서버가 범위 끝을 오늘로 잡는다).
    "오늘",
  ];
}

function shortDate(isoDate: string): string {
  const [, month, day] = isoDate.split("-");
  return `${Number(month)}.${Number(day)}`;
}

function share(count: number, referrers: { count: number }[]): number {
  const total = referrers.reduce((sum, referrer) => sum + referrer.count, 0);
  return total === 0 ? 0 : Math.round((count / total) * 100);
}

/** 마지막 방문. 90일 넘게 방문이 없으면 원시가 지워져 값이 없다. */
function formatDate(isoDateTime: string | null): string {
  if (!isoDateTime) {
    return "—";
  }
  const date = new Date(isoDateTime);
  return `${date.getMonth() + 1}.${date.getDate()}`;
}
