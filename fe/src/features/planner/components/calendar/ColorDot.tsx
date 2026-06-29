import { cn } from "@/lib/utils";

interface ColorDotProps {
  /** 색 클래스(예: EVENT_DOT, BUCKET_DOT[...]). */
  className?: string;
  /** xs=셀 점(size-1.5), sm=범례 점(size-2). */
  size?: "xs" | "sm";
}

/** 캘린더 소스/버킷 색 점. 셀·범례 공용. */
export function ColorDot({ className, size = "sm" }: ColorDotProps) {
  return (
    <span
      className={cn(
        "shrink-0 rounded-full",
        size === "xs" ? "size-1.5" : "size-2",
        className,
      )}
    />
  );
}
