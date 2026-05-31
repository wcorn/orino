import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { MATERIAL_TYPE_ICONS } from "@/features/material/utils";

import type { CalendarReview } from "../../api/calendar";
import {
  classifyReview,
  parseIsoDate,
  type ReviewBucket,
} from "../../calendar";
import { BUCKET_ICON, BUCKET_LABEL, BUCKET_ORDER } from "./bucketStyles";

interface Props {
  isoDate: string;
  reviews: CalendarReview[];
  today: Date;
}

function formatHeading(isoDate: string): string {
  const d = parseIsoDate(isoDate);
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

export function DayDetailPanel({ isoDate, reviews, today }: Props) {
  const byBucket = new Map<ReviewBucket, CalendarReview[]>();
  for (const r of reviews) {
    const bucket = classifyReview(r, today);
    const list = byBucket.get(bucket);
    if (list) {
      list.push(r);
    } else {
      byBucket.set(bucket, [r]);
    }
  }

  const hasActionable =
    (byBucket.get("overdue")?.length ?? 0) > 0 ||
    (byBucket.get("today")?.length ?? 0) > 0;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-medium">{formatHeading(isoDate)}</h2>
        {hasActionable && (
          <Link to="/planner/reviews/today">
            <Button size="sm">오늘 복습 하러가기</Button>
          </Link>
        )}
      </div>

      {reviews.length === 0 ? (
        <p className="text-muted-foreground text-sm">복습 일정이 없어요.</p>
      ) : (
        <div className="flex flex-col gap-4">
          {BUCKET_ORDER.filter((b) => (byBucket.get(b)?.length ?? 0) > 0).map(
            (bucket) => (
              <section key={bucket} className="flex flex-col gap-2">
                <h3 className="text-muted-foreground text-xs font-medium">
                  {BUCKET_ICON[bucket]} {BUCKET_LABEL[bucket]} (
                  {byBucket.get(bucket)!.length})
                </h3>
                <ul className="flex flex-col gap-1.5">
                  {byBucket.get(bucket)!.map((r) => (
                    <li
                      key={r.id}
                      className="border-border bg-card flex items-start gap-2 rounded-md border p-2 text-sm"
                    >
                      <span aria-hidden>
                        {MATERIAL_TYPE_ICONS[r.flashcard.material.type]}
                      </span>
                      <div className="flex min-w-0 flex-1 flex-col">
                        <span className="text-muted-foreground truncate text-xs">
                          {r.flashcard.material.title} · {r.sequence}회차
                          {r.status === "COMPLETED" && r.rating
                            ? ` · ${r.rating}`
                            : ""}
                        </span>
                        <span className="truncate">{r.flashcard.front}</span>
                      </div>
                    </li>
                  ))}
                </ul>
              </section>
            ),
          )}
        </div>
      )}
    </div>
  );
}
