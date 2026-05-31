import { cn } from "@/lib/utils";

import type { ReviewBucket } from "../../calendar";
import { BUCKET_DOT, BUCKET_LABEL, BUCKET_ORDER } from "./bucketStyles";

export function CalendarLegend() {
  return (
    <ul className="text-muted-foreground flex flex-wrap gap-x-3 gap-y-1 text-xs">
      {BUCKET_ORDER.map((bucket: ReviewBucket) => (
        <li key={bucket} className="flex items-center gap-1.5">
          <span className={cn("size-2 rounded-full", BUCKET_DOT[bucket])} />
          {BUCKET_LABEL[bucket]}
        </li>
      ))}
    </ul>
  );
}
