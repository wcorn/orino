import * as React from "react";

import { cn } from "@/lib/utils";

/**
 * 빈 상태·완료 상태 등 중앙 정렬 안내 레이아웃. 내용(메시지·액션 버튼)은 children으로 구성한다.
 * 높이는 기본 min-h-[40svh]이며 className(min-h-[30svh] 등)으로 덮어쓴다.
 */
function EmptyState({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="empty-state"
      className={cn(
        "flex min-h-[40svh] flex-col items-center justify-center gap-4 text-center",
        className,
      )}
      {...props}
    />
  );
}

export { EmptyState };
