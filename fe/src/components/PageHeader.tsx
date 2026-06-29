import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

interface PageHeaderProps {
  /** 페이지 제목. */
  title: ReactNode;
  /** 제목 아래 보조 설명(선택). */
  description?: ReactNode;
  /** 우측 정렬 액션 슬롯(버튼 등, 선택). */
  actions?: ReactNode;
  className?: string;
}

/**
 * 페이지 상단 헤더 — 모든 페이지가 동일한 제목 크기(text-display 토큰)·여백·정렬을 쓰도록 통일한다.
 * 우측 액션과 보조 설명은 선택 슬롯.
 */
export function PageHeader({
  title,
  description,
  actions,
  className,
}: PageHeaderProps) {
  return (
    <header
      className={cn("flex items-center justify-between gap-3", className)}
    >
      <div className="min-w-0">
        <h1 className="text-display font-semibold">{title}</h1>
        {description && (
          <p className="text-muted-foreground mt-0.5 text-sm">{description}</p>
        )}
      </div>
      {actions && (
        <div className="flex shrink-0 items-center gap-2">{actions}</div>
      )}
    </header>
  );
}
