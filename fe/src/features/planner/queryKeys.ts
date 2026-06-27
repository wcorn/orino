export const plannerKeys = {
  all: ["planner"] as const,
  calendar: (from: string, to: string) =>
    ["planner", "calendar", from, to] as const,
  weeklyPlan: () => ["planner", "weeklyPlan"] as const,
};

export const routineKeys = {
  all: ["routine"] as const,
  list: () => ["routine", "list"] as const,
};

export const holidayKeys = {
  all: ["holiday"] as const,
  range: (from: string, to: string) => ["holiday", from, to] as const,
};
