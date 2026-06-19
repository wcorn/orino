import * as React from "react";

import { cn } from "@/lib/utils";

/** 폼 필드·데이터 로드 실패 등 사용자에게 보이는 오류 메시지. */
function FieldError({ className, ...props }: React.ComponentProps<"p">) {
  return (
    <p
      data-slot="field-error"
      className={cn("text-destructive text-sm", className)}
      {...props}
    />
  );
}

export { FieldError };
