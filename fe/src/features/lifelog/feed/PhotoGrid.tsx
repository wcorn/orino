import { cn } from "@/lib/utils";

import type { MomentPhoto } from "../api/types";

/** 기록 카드의 사진 그리드. 1장=풀폭, 2~4장=그리드, 5장+는 마지막에 +N 오버레이. */
export function PhotoGrid({ photos }: { photos: MomentPhoto[] }) {
  if (photos.length === 0) return null;
  const shown = photos.slice(0, 4);
  const extra = photos.length - shown.length;

  return (
    <div
      className={cn(
        "grid gap-1 overflow-hidden rounded-lg",
        shown.length === 1 ? "grid-cols-1" : "grid-cols-2",
      )}
    >
      {shown.map((photo, i) => (
        <a
          key={photo.id}
          href={photo.url}
          target="_blank"
          rel="noreferrer"
          className={cn(
            "bg-muted relative block",
            shown.length === 1 ? "aspect-video" : "aspect-square",
          )}
        >
          <img
            src={photo.thumbUrl ?? photo.url}
            alt=""
            loading="lazy"
            className="size-full object-cover"
          />
          {i === shown.length - 1 && extra > 0 && (
            <span className="bg-foreground/50 text-background absolute inset-0 flex items-center justify-center text-lg font-semibold">
              +{extra}
            </span>
          )}
        </a>
      ))}
    </div>
  );
}
