import type { NextReview } from "./api/flashcards";

export function formatNextReview(
  next: NextReview,
  today: Date = new Date(),
): string {
  const d = parseDate(next.scheduledAt.slice(0, 10));
  const todayMid = new Date(
    today.getFullYear(),
    today.getMonth(),
    today.getDate(),
  );
  const daysDiff = Math.round(
    (d.getTime() - todayMid.getTime()) / (1000 * 60 * 60 * 24),
  );

  const md = `${d.getMonth() + 1}/${d.getDate()}`;
  const relative =
    daysDiff === 0
      ? "오늘"
      : daysDiff > 0
        ? `${daysDiff}일 후`
        : `${-daysDiff}일 지남`;
  return `${md} (${relative}) · ${next.sequence}회차`;
}

function parseDate(isoDate: string): Date {
  const [y, m, d] = isoDate.split("-").map(Number);
  return new Date(y, m - 1, d);
}
