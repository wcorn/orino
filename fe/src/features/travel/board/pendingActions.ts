import { create } from "zustand";

/**
 * "5초 안에 되돌릴 수 있는" 일정 동작의 보류함.
 *
 * <p><b>실행취소는 서버 기능이 아니다</b>(결정 기록 D-5). 소프트 삭제·복원 API를 두는 대신
 * 화면이 요청을 미루고 낙관적으로만 반영한다 — 되돌리면 요청 자체가 나가지 않으므로
 * 서버에는 애초에 아무 일도 없었던 게 된다.
 *
 * <p>컴포넌트가 아니라 <b>모듈 수준</b>에 두는 이유가 있다. 일정 상세에서 삭제하면 곧바로
 * 보드로 돌아가는데, 보류함이 화면에 묶여 있으면 그 이동만으로 되돌릴 기회가 사라진다.
 * 타이머는 스낵바(역시 모듈 수준)가 들고 있으므로 화면을 옮겨도 계속 흐른다.
 */
interface PendingActionsState {
  /** 낙관적으로 감출 일정 id. */
  pendingIds: number[];
  /** 아직 보내지 않은 실행 함수. */
  commits: Map<number, () => void>;
  defer: (id: number, commit: () => void) => void;
  /** 시간이 다 됐다 — 진짜로 보낸다. */
  commit: (id: number) => void;
  /** 되돌린다 — 요청을 보내지 않고 버린다. */
  cancel: (id: number) => void;
  /** 남은 것을 전부 즉시 보낸다(탭을 닫을 때). */
  flushAll: () => void;
}

/**
 * 보류함을 하나 만든다. <b>id 이름공간마다 따로 둔다.</b>
 *
 * <p>일정 5번과 준비 항목 5번은 다른 것인데 보류함을 함께 쓰면 같은 숫자로 만난다 —
 * 일정을 지운 채 준비 화면을 열면 준비 5번이 이유 없이 사라져 보이고, 되돌리기 한 번이
 * 엉뚱한 요청을 취소한다. 문자열 키로 바꿔 접두사를 붙이는 길도 있지만, 그러면 화면마다
 * 접두사를 기억해야 한다. 보류함을 나누면 타입이 알아서 갈라 준다.
 */
function createPendingActions() {
  return create<PendingActionsState>((set, get) => ({
    pendingIds: [],
    commits: new Map(),

    defer: (id, commit) =>
      set((state) => {
        const commits = new Map(state.commits);
        commits.set(id, commit);
        return {
          commits,
          pendingIds: state.pendingIds.includes(id)
            ? state.pendingIds
            : [...state.pendingIds, id],
        };
      }),

    commit: (id) => {
      const run = get().commits.get(id);
      get().cancel(id);
      run?.();
    },

    cancel: (id) =>
      set((state) => {
        const commits = new Map(state.commits);
        commits.delete(id);
        return {
          commits,
          pendingIds: state.pendingIds.filter((p) => p !== id),
        };
      }),

    flushAll: () => {
      const { commits } = get();
      set({ commits: new Map(), pendingIds: [] });
      commits.forEach((run) => run());
    },
  }));
}

/** 일정(보드·일정 상세)의 보류함. */
export const usePendingActions = createPendingActions();

/** 준비 항목의 보류함. 일정과 id 이름공간이 달라 따로 둔다. */
export const usePendingPrepActions = createPendingActions();

/**
 * 탭을 닫거나 새로고침하면 남은 것을 보낸다. 그냥 사라지면 사용자는 지웠다고 믿는데
 * 서버에는 남는다. (앱 안에서의 화면 이동은 타이머가 그대로 살아 있어 flush가 필요 없다.)
 */
if (typeof window !== "undefined") {
  window.addEventListener("pagehide", () => {
    usePendingActions.getState().flushAll();
    usePendingPrepActions.getState().flushAll();
  });
}
