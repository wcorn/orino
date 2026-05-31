import { useQuery } from "@tanstack/react-query";

import { fetchCalendarReviews } from "../api/calendar";
import { reviewKeys } from "../queryKeys";

export function useCalendarReviews(from: string, to: string) {
  return useQuery({
    queryKey: reviewKeys.calendar(from, to),
    queryFn: () => fetchCalendarReviews(from, to),
    staleTime: 60 * 1000,
  });
}
