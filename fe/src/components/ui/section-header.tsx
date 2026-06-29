import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

interface SectionHeaderProps {
  children: ReactNode;
  /** md=섹션 제목(text-base 진하게), sm=하위 묶음 제목(text-xs 흐리게). */
  size?: "sm" | "md";
  /** 제목 계층(h2/h3). */
  level?: 2 | 3;
  className?: string;
}

/** 페이지 내부 섹션/하위 묶음 제목. h1 페이지 제목은 PageHeader를 쓴다. */
export function SectionHeader({
  children,
  size = "md",
  level = 2,
  className,
}: SectionHeaderProps) {
  const Tag = level === 3 ? "h3" : "h2";
  return (
    <Tag
      className={cn(
        size === "sm"
          ? "text-muted-foreground text-xs font-medium"
          : "text-base font-semibold",
        className,
      )}
    >
      {children}
    </Tag>
  );
}
