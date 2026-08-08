import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useRef, useState } from "react";

import {
  type ActivityLog,
  type ActivityLogRequest,
  saveActivityLog,
} from "@/features/travel/api/activities";
import { travelKeys } from "@/features/travel/queryKeys";

export type LogSaveStatus = "idle" | "saving" | "saved" | "error";

/**
 * 여행 중에 "저장" 버튼을 찾게 하지 않는다. 짧게 잡으면 한 글자마다 요청이 나가고,
 * 길게 잡으면 화면을 닫을 때 놓친다.
 */
const DEBOUNCE_MS = 1000;

interface UseAutoSaveLogResult {
  status: LogSaveStatus;
  schedule: (patch: ActivityLogRequest) => void;
  /** 화면을 떠나기 전에 대기 중인 저장을 즉시 보낸다. */
  flush: () => void;
}

/**
 * 기록(평점·메모) 자동 저장.
 *
 * <p><b>보류 중인 값을 ref로 들고 언마운트에서 flush한다.</b> 뒤로 가기가 디바운스보다
 * 빠른 게 정상이라, 타이머에만 기대면 방금 누른 별이 조용히 사라진다.
 */
export function useAutoSaveLog(
  activityId: number,
  tripId: number | null,
): UseAutoSaveLogResult {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<LogSaveStatus>("idle");
  const pendingRef = useRef<ActivityLogRequest | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // 최신 값을 ref로도 들고 있어야 언마운트 클린업이 옛 클로저를 보지 않는다.
  const contextRef = useRef({ activityId, tripId, queryClient });
  contextRef.current = { activityId, tripId, queryClient };

  const send = useCallback(async (body: ActivityLogRequest) => {
    const {
      activityId: id,
      tripId: trip,
      queryClient: qc,
    } = contextRef.current;
    setStatus("saving");
    try {
      const log: ActivityLog | null = await saveActivityLog(id, body);
      // 상세 캐시를 직접 갱신한다 — 재조회하면 방금 친 글자가 서버 값으로 덮인다.
      qc.setQueryData(travelKeys.activity(id), (prev: unknown) =>
        prev ? { ...prev, log, hasLog: log !== null } : prev,
      );
      // 보드는 기록 표시를 쓰므로 다음 조회 때 새로 받게 둔다.
      if (trip !== null) {
        void qc.invalidateQueries({ queryKey: travelKeys.boards(trip) });
      }
      setStatus("saved");
    } catch {
      setStatus("error");
    }
  }, []);

  const fire = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    const pending = pendingRef.current;
    pendingRef.current = null;
    if (pending) void send(pending);
  }, [send]);

  const schedule = useCallback(
    (patch: ActivityLogRequest) => {
      pendingRef.current = patch;
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(fire, DEBOUNCE_MS);
    },
    [fire],
  );

  // 화면을 떠날 때 대기 중인 값을 보낸다. 타이머만 끄면 그 입력은 사라진다.
  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
      const pending = pendingRef.current;
      pendingRef.current = null;
      if (pending) {
        const { activityId: id } = contextRef.current;
        void saveActivityLog(id, pending).catch(() => {
          // 화면이 이미 사라진 뒤라 알릴 곳이 없다. 다음 진입에서 서버 값이 보인다.
        });
      }
    };
  }, []);

  return { status, schedule, flush: fire };
}
