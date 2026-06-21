import { useQuery } from "@tanstack/react-query";

import { fetchHolidays } from "../api/holidays";
import { holidayKeys } from "../queryKeys";

/** [from, to] 구간 공휴일 조회. 거의 변하지 않으므로 1시간 staleTime. */
export function useHolidays(from: string, to: string) {
  return useQuery({
    queryKey: holidayKeys.range(from, to),
    queryFn: () => fetchHolidays(from, to),
    staleTime: 60 * 60 * 1000,
  });
}
