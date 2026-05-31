import type { ReviewBucket } from "../../calendar";

export const BUCKET_DOT: Record<ReviewBucket, string> = {
  overdue: "bg-red-500",
  today: "bg-primary",
  upcoming: "bg-muted-foreground/40",
  completed: "bg-foreground/30",
};

export const BUCKET_LABEL: Record<ReviewBucket, string> = {
  overdue: "밀림",
  today: "오늘",
  upcoming: "예정",
  completed: "완료",
};

export const BUCKET_ICON: Record<ReviewBucket, string> = {
  overdue: "🔴",
  today: "🟣",
  upcoming: "⚪",
  completed: "✅",
};

// 셀에서 점을 표시하는 순서 (중요도 높은 순)
export const BUCKET_ORDER: ReviewBucket[] = [
  "overdue",
  "today",
  "upcoming",
  "completed",
];
