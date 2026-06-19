import * as React from "react";

import { cn } from "@/lib/utils";

/** 데이터 로딩 중 표시하는 표준 안내 텍스트. 기본 문구 "불러오는 중…". */
function LoadingText({
  className,
  children = "불러오는 중…",
  ...props
}: React.ComponentProps<"p">) {
  return (
    <p
      data-slot="loading-text"
      className={cn("text-muted-foreground text-sm", className)}
      {...props}
    >
      {children}
    </p>
  );
}

export { LoadingText };
