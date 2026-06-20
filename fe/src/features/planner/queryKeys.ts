export const plannerKeys = {
  all: ["planner"] as const,
  calendar: (from: string, to: string) =>
    ["planner", "calendar", from, to] as const,
};

export const routineKeys = {
  all: ["routine"] as const,
  list: () => ["routine", "list"] as const,
};
