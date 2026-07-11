import type {
  CompletedReviewParams,
  UpcomingReviewParams,
} from "./api/reviewHub";

export const reviewKeys = {
  all: ["reviews"] as const,
  today: ["reviews", "today"] as const,
  calendar: (from: string, to: string) =>
    ["reviews", "calendar", from, to] as const,
  summary: ["reviews", "summary"] as const,
  upcoming: (filters: Omit<UpcomingReviewParams, "cursor" | "size">) =>
    ["reviews", "upcoming", filters] as const,
  completed: (filters: Omit<CompletedReviewParams, "cursor" | "size">) =>
    ["reviews", "completed", filters] as const,
};
