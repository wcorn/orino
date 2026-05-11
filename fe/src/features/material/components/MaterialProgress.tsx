import { cn } from "@/lib/utils";

interface MaterialProgressProps {
  completed: number;
  total: number;
  className?: string;
}

export function MaterialProgress({
  completed,
  total,
  className,
}: MaterialProgressProps) {
  const percent = total === 0 ? 0 : Math.round((completed / total) * 100);

  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      <div
        role="progressbar"
        aria-valuenow={percent}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={`진행률 ${percent}%`}
        className="bg-muted h-1.5 w-full overflow-hidden rounded-full"
      >
        <div
          className="bg-primary h-full transition-all"
          style={{ width: `${percent}%` }}
        />
      </div>
      <p className="text-muted-foreground text-xs">
        {completed} / {total} ({percent}%)
      </p>
    </div>
  );
}
