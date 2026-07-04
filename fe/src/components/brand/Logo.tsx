import * as React from "react";

import { cn } from "@/lib/utils";

type LogoTone = "primary" | "mono" | "inverse";

// 링/점 색은 토큰(--foreground/--brand)만 쓴다. inverse만 어두운 배경용 흰색.
const RING: Record<LogoTone, string> = {
  primary: "var(--foreground)",
  mono: "var(--brand)",
  inverse: "#ffffff",
};
const DOT: Record<LogoTone, string> = {
  primary: "var(--brand)",
  mono: "var(--brand)",
  inverse: "#ffffff",
};

/** orino 심볼(2a Orbit) — 궤도 틈은 마스크로 관통해 배경색과 무관하게 유지된다. 좌표는 브랜드 확정본. */
export function BrandMark({
  size = 24,
  tone = "primary",
  className,
}: {
  size?: number;
  tone?: LogoTone;
  className?: string;
}) {
  const id = React.useId();
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      fill="none"
      className={className}
      role="img"
      aria-label="orino"
    >
      <mask id={id}>
        <rect width="100" height="100" fill="#fff" />
        <circle cx="74.35" cy="25.65" r="15.51" fill="#000" />
      </mask>
      <circle
        cx="49.06"
        cy="50.94"
        r="29.14"
        fill="none"
        stroke={RING[tone]}
        strokeWidth="13.16"
        mask={`url(#${id})`}
      />
      <circle cx="74.35" cy="25.65" r="12.22" fill={DOT[tone]} />
    </svg>
  );
}

/** 심볼 + 워드마크. 앱바·헤더 등에 사용. showWordmark=false면 심볼만. */
export function Logo({
  size = 28,
  tone = "primary",
  showWordmark = true,
  className,
}: {
  size?: number;
  tone?: LogoTone;
  showWordmark?: boolean;
  className?: string;
}) {
  return (
    <span className={cn("inline-flex items-center gap-2.5", className)}>
      <BrandMark size={size} tone={tone} />
      {showWordmark && (
        <span
          className={cn(
            "font-bold tracking-tight",
            tone === "inverse" ? "text-white" : "text-foreground",
          )}
          style={{ fontSize: size * 0.95, lineHeight: 1 }}
        >
          orino
        </span>
      )}
    </span>
  );
}
