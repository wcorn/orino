import * as React from "react";

import { cn } from "@/lib/utils";

import { FieldError } from "./field-error";

interface FormFieldProps {
  label: React.ReactNode;
  /** label의 htmlFor / 컨트롤의 id. 컨트롤이 자체 라벨을 쓰면 생략 가능. */
  htmlFor?: string;
  /** 네이티브 input이 아닌 컨트롤(Select 등)을 aria-labelledby로 연결할 때 label의 id. */
  labelId?: string;
  error?: string;
  className?: string;
  children: React.ReactNode;
}

/** label + 컨트롤 + (선택)에러를 묶는 표준 폼 필드 레이아웃. */
function FormField({
  label,
  htmlFor,
  labelId,
  error,
  className,
  children,
}: FormFieldProps) {
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      <label id={labelId} htmlFor={htmlFor} className="text-sm font-medium">
        {label}
      </label>
      {children}
      {error && <FieldError>{error}</FieldError>}
    </div>
  );
}

export { FormField };
