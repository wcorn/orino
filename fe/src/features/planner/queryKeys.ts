export const plannerKeys = {
  all: ["planner"] as const,
  calendar: (from: string, to: string) =>
    ["planner", "calendar", from, to] as const,
};
