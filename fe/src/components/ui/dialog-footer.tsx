import * as React from "react";

import { cn } from "@/lib/utils";

/**
 * 다이얼로그 하단 액션 영역. 기본은 오른쪽 정렬(취소+제출). 삭제 버튼을 왼쪽에 두는 경우
 * className에 justify-between을 넘겨 정렬을 바꾼다.
 */
function DialogFooter({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="dialog-footer"
      className={cn("mt-5 flex items-center justify-end gap-2", className)}
      {...props}
    />
  );
}

export { DialogFooter };
