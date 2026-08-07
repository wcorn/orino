import { useCallback, useEffect, useRef, useState } from "react";

/**
 * "5초 안에 되돌릴 수 있는" 동작을 보류해 둔다.
 *
 * <p><b>실행취소는 서버 기능이 아니다</b>(결정 기록 D-5). 소프트 삭제·복원 API를 두는 대신
 * 화면이 요청을 5초 미루고 낙관적으로만 반영한다 — 되돌리면 요청 자체가 나가지 않으므로
 * 서버에는 애초에 아무 일도 없었던 게 된다.
 *
 * <p>대신 <b>보류 중에 화면을 떠나면 즉시 보내야 한다.</b> 그냥 사라지면 사용자는 지웠다고
 * 믿는데 서버에는 남는다.
 */
export function useDeferredCommits() {
  // 렌더에 쓰는 목록(숨길 id)과 언마운트 flush에 쓰는 실행 함수를 나눠 둔다.
  const commits = useRef(new Map<number, () => void>());
  const [pendingIds, setPendingIds] = useState<number[]>([]);

  const defer = useCallback((id: number, commit: () => void) => {
    commits.current.set(id, commit);
    setPendingIds((prev) => (prev.includes(id) ? prev : [...prev, id]));
  }, []);

  /** 시간이 다 됐다 — 진짜로 보낸다. */
  const commit = useCallback((id: number) => {
    const run = commits.current.get(id);
    commits.current.delete(id);
    setPendingIds((prev) => prev.filter((p) => p !== id));
    run?.();
  }, []);

  /** 되돌린다 — 요청을 보내지 않고 버린다. */
  const cancel = useCallback((id: number) => {
    commits.current.delete(id);
    setPendingIds((prev) => prev.filter((p) => p !== id));
  }, []);

  useEffect(() => {
    const map = commits.current;
    return () => {
      // 화면 이탈·언마운트 — 보류 중인 것을 전부 즉시 보낸다.
      map.forEach((run) => run());
      map.clear();
    };
  }, []);

  return { defer, commit, cancel, pendingIds };
}
