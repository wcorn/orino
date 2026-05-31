export const reviewKeys = {
  all: ["reviews"] as const,
  today: ["reviews", "today"] as const,
  calendar: (from: string, to: string) =>
    ["reviews", "calendar", from, to] as const,
};
