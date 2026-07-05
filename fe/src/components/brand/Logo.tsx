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

function useReducedMotion() {
  const [reduced, setReduced] = React.useState(false);
  React.useEffect(() => {
    // matchMedia가 없는 환경(SSR·테스트)에서는 애니메이션을 켠 채로 둔다.
    if (typeof window.matchMedia !== "function") return;
    const m = window.matchMedia("(prefers-reduced-motion: reduce)");
    const on = () => setReduced(m.matches);
    on();
    m.addEventListener("change", on);
    return () => m.removeEventListener("change", on);
  }, []);
  return reduced;
}

/**
 * orino 심볼(2a Orbit). 궤도 틈은 마스크로 관통해 배경색과 무관하게 유지된다. 좌표는 브랜드 확정본.
 * - animated=false: 정적(우상단 틈에 점이 쉼) — 파비콘/기본
 * - animated=true : 키네틱(점이 링을 돌고 틈이 함께 이동) — 스플래시/로딩(장치 2).
 *   prefers-reduced-motion이면 자동 정지(상단에 점이 쉼).
 */
export function BrandMark({
  size = 24,
  tone = "primary",
  animated = false,
  className,
}: {
  size?: number;
  tone?: LogoTone;
  animated?: boolean;
  className?: string;
}) {
  const id = React.useId();
  const reduce = useReducedMotion();
  const spin: React.CSSProperties | undefined =
    animated && !reduce
      ? {
          transformOrigin: "49.14px 50.86px",
          animation: "orino-orbit 3.4s linear infinite",
        }
      : undefined;

  // 정적: 우상단 고정 틈+점 / 키네틱: 상단(12시)에서 시작해 회전 그룹으로 돈다.
  const dotCx = animated ? "49.14" : "72.27";
  const dotCy = animated ? "24.2" : "27.73";
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
        <g style={spin}>
          <circle cx={dotCx} cy={dotCy} r="12.9" fill="#000" />
        </g>
      </mask>
      <circle
        cx="49.14"
        cy="50.86"
        r="26.66"
        fill="none"
        stroke={RING[tone]}
        strokeWidth="9.46"
        mask={`url(#${id})`}
      />
      <g style={spin}>
        <circle cx={dotCx} cy={dotCy} r="10.32" fill={DOT[tone]} />
      </g>
    </svg>
  );
}

/**
 * 워드마크(장치 1: 궤도 i-점). i는 점 없는 ı(U+0131), 그 위에 심볼의 궤도 점과 같은
 * 퍼플·원형 점을 얹어 마크와 글자를 하나의 시스템으로 묶는다. 항상 소문자.
 */
export function Wordmark({
  size = 26,
  tone = "primary",
  className,
}: {
  size?: number;
  tone?: LogoTone;
  className?: string;
}) {
  const dotColor = tone === "inverse" ? "#ffffff" : "var(--brand)";
  return (
    <span
      role="img"
      aria-label="orino"
      className={cn(
        "font-bold tracking-tight",
        tone === "inverse" ? "text-white" : "text-foreground",
        className,
      )}
      style={{ fontSize: size, lineHeight: 1 }}
    >
      or
      <span style={{ position: "relative" }} aria-hidden>
        {"ı"}
        <span
          style={{
            position: "absolute",
            left: "50%",
            top: "0.03em",
            transform: "translateX(-50%)",
            width: "0.22em",
            height: "0.22em",
            borderRadius: "50%",
            background: dotColor,
          }}
        />
      </span>
      no
    </span>
  );
}

/** 락업(심볼 + i-점 워드마크). 앱바·헤더 등에 사용. animated면 심볼이 키네틱. */
export function Logo({
  size = 28,
  tone = "primary",
  showWordmark = true,
  animated = false,
  className,
}: {
  size?: number;
  tone?: LogoTone;
  showWordmark?: boolean;
  animated?: boolean;
  className?: string;
}) {
  return (
    <span className={cn("inline-flex items-center gap-2.5", className)}>
      <BrandMark size={size} tone={tone} animated={animated} />
      {showWordmark && <Wordmark size={size * 0.95} tone={tone} />}
    </span>
  );
}
