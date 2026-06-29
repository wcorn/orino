import {
  BUCKET_DOT,
  BUCKET_LABEL,
  BUCKET_ORDER,
} from "@/features/review/components/calendar/bucketStyles";

import { ColorDot } from "./ColorDot";
import { EVENT_DOT, EVENT_LABEL, TASK_DOT, TASK_LABEL } from "./sourceStyles";

const SOURCE_ITEMS = [
  { dot: EVENT_DOT, label: EVENT_LABEL },
  { dot: TASK_DOT, label: TASK_LABEL },
];

export function PlannerCalendarLegend() {
  return (
    <ul className="text-muted-foreground flex flex-wrap gap-x-3 gap-y-1 text-xs">
      {SOURCE_ITEMS.map((item) => (
        <li key={item.label} className="flex items-center gap-1.5">
          <ColorDot className={item.dot} />
          {item.label}
        </li>
      ))}
      {BUCKET_ORDER.map((bucket) => (
        <li key={bucket} className="flex items-center gap-1.5">
          <ColorDot className={BUCKET_DOT[bucket]} />
          {BUCKET_LABEL[bucket]}
        </li>
      ))}
    </ul>
  );
}
