import * as React from "react";

import { cn } from "@/lib/utils";

function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        "border-input bg-background focus-visible:ring-ring/30 min-h-[3rem] w-full resize-y rounded-md border p-2 text-sm shadow-xs focus-visible:ring-2 focus-visible:outline-none",
        className,
      )}
      {...props}
    />
  );
}

export { Textarea };
