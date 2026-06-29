import { cva, type VariantProps } from "class-variance-authority";
import * as React from "react";

import { cn } from "@/lib/utils";

const alertVariants = cva(
  "relative grid w-full grid-cols-[0_1fr] items-start gap-y-0.5 rounded-lg border px-4 py-3 text-sm has-[>svg]:grid-cols-[1.25rem_1fr] has-[>svg]:gap-x-3 [&>svg]:size-4 [&>svg]:translate-y-0.5 [&>svg]:shrink-0",
  {
    variants: {
      variant: {
        default:
          "bg-card text-card-foreground border-border [&>svg]:text-foreground",
        info: "border-info/30 bg-info/10 text-foreground [&>svg]:text-info",
        success:
          "border-success/30 bg-success/10 text-foreground [&>svg]:text-success",
        warning:
          "border-warning/30 bg-warning/10 text-foreground [&>svg]:text-warning",
        destructive:
          "border-destructive/30 bg-destructive/10 text-foreground [&>svg]:text-destructive",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  },
);

/** 인라인 알림. 상태색은 시맨틱 토큰만(info/success/warning/destructive). 아이콘은 svg 자식으로. */
function Alert({
  className,
  variant,
  ...props
}: React.ComponentProps<"div"> & VariantProps<typeof alertVariants>) {
  return (
    <div
      role="alert"
      data-slot="alert"
      className={cn(alertVariants({ variant }), className)}
      {...props}
    />
  );
}

function AlertTitle({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="alert-title"
      className={cn(
        "col-start-2 font-medium tracking-tight whitespace-nowrap",
        className,
      )}
      {...props}
    />
  );
}

function AlertDescription({
  className,
  ...props
}: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="alert-description"
      className={cn(
        "text-muted-foreground col-start-2 text-sm [&_p]:leading-relaxed",
        className,
      )}
      {...props}
    />
  );
}

export { Alert, AlertDescription, AlertTitle };
