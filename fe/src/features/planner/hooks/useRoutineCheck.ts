import { useMutation, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import type { PlannerCalendarFeed, PlannerEvent } from "../api/feed";
import { checkRoutine } from "../api/routines";
import { plannerKeys } from "../queryKeys";

interface CheckVars {
  recurringEventId: string;
  /** 인스턴스 날짜 "2026-06-20" */
  date: string;
  done: boolean;
}

function matches(event: PlannerEvent, vars: CheckVars): boolean {
  return (
    event.routine?.recurringEventId === vars.recurringEventId &&
    event.start.slice(0, 10) === vars.date
  );
}

function applyDone(
  feed: PlannerCalendarFeed,
  vars: CheckVars,
): PlannerCalendarFeed {
  return {
    ...feed,
    events: feed.events.map((event) =>
      matches(event, vars) && event.routine
        ? { ...event, routine: { ...event.routine, done: vars.done } }
        : event,
    ),
  };
}

/**
 * 습관 완료 체크 토글(낙관적). 모든 캘린더 피드 캐시에서 해당 인스턴스 done을 즉시 뒤집고,
 * 실패 시 스냅샷으로 롤백 + 토스트한다. 성공/실패와 무관하게 마지막에 재검증한다.
 */
export function useRoutineCheck() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ recurringEventId, date, done }: CheckVars) =>
      checkRoutine(recurringEventId, date, done),
    onMutate: async (vars) => {
      await queryClient.cancelQueries({ queryKey: plannerKeys.all });
      const snapshots = queryClient.getQueriesData<PlannerCalendarFeed>({
        queryKey: plannerKeys.all,
      });
      queryClient.setQueriesData<PlannerCalendarFeed>(
        { queryKey: plannerKeys.all },
        (feed) => (feed ? applyDone(feed, vars) : feed),
      );
      return { snapshots };
    },
    onError: (_error, _vars, context) => {
      context?.snapshots.forEach(([key, data]) =>
        queryClient.setQueryData(key, data),
      );
      toast("체크 변경에 실패했습니다.", "error");
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: plannerKeys.all });
    },
  });
}
