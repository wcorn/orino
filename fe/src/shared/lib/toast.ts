import { create } from "zustand";

export type ToastVariant = "info" | "success" | "error";

export interface ToastAction {
  /** 버튼 라벨(예: "실행취소"). */
  label: string;
  /** 누르면 실행하고 스낵바를 닫는다. */
  onAction: () => void;
}

export interface ToastItem {
  id: string;
  message: string;
  variant: ToastVariant;
  action?: ToastAction;
  /** 이 스낵바가 사라질 때까지의 시간(ms). 남은 시간 표시에 쓴다. */
  durationMs: number;
  /** 사라질 시각(epoch ms). 카운트다운 계산 기준. */
  expiresAt: number;
}

const AUTO_DISMISS_MS = 3500;
/** 실행취소를 줄 때의 기본 지속시간. 되돌릴 여유와 대기 사이의 절충값이다. */
export const UNDO_DURATION_MS = 5000;

let counter = 0;

interface ShowOptions {
  variant?: ToastVariant;
  action?: ToastAction;
  /** `Infinity`면 스스로 닫히지 않는다 — 사용자가 액션을 누르거나 닫아야 한다. */
  durationMs?: number;
}

interface ToastState {
  toasts: ToastItem[];
  show: (message: string, options?: ShowOptions) => string;
  /** 닫기 — 시간이 다 됐을 때, 사용자가 닫을 때, 액션을 눌렀을 때 모두 이것이다. */
  dismiss: (id: string) => void;
  runAction: (id: string) => void;
}

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],

  show: (message, options = {}) => {
    const {
      variant = "info",
      action,
      durationMs = action ? UNDO_DURATION_MS : AUTO_DISMISS_MS,
    } = options;
    const id = `t-${Date.now()}-${++counter}`;
    set((state) => ({
      toasts: [
        ...state.toasts,
        {
          id,
          message,
          variant,
          action,
          durationMs,
          expiresAt: Date.now() + durationMs,
        },
      ],
    }));
    // durationMs가 유한하지 않으면 타이머를 걸지 않는다.
    // setTimeout(fn, Infinity)는 0으로 강제돼 오히려 즉시 사라진다.
    if (Number.isFinite(durationMs)) {
      setTimeout(() => get().dismiss(id), durationMs);
    }
    return id;
  },

  dismiss: (id) =>
    set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),

  runAction: (id) => {
    const item = get().toasts.find((t) => t.id === id);
    if (!item) return;
    // 먼저 닫는다 — onAction이 새 스낵바를 띄우더라도 이 항목이 남지 않게.
    get().dismiss(id);
    item.action?.onAction();
  },
}));

export function toast(message: string, variant: ToastVariant = "info") {
  return useToastStore.getState().show(message, { variant });
}

interface UndoOptions {
  /** 실행취소를 누르면 실행. 되돌리는 일 자체는 호출부가 한다. */
  onUndo: () => void;
  durationMs?: number;
}

/**
 * 실행취소 스낵바. 5초 안에 누르면 `onUndo`가 실행된다.
 *
 * <p><b>이미 끝난 일을 되돌리는 버튼일 뿐이다.</b> 요청을 이 스낵바가 닫힐 때까지 미뤄 두는
 * 쓰임은 없앴다(#1432) — 누른 일은 그 자리에서 실행하고, 되돌리기는 같은 값으로 다시 적는
 * 식으로 호출부가 한다(경비 삭제가 그렇다).
 */
export function toastUndo(message: string, options: UndoOptions) {
  const { onUndo, durationMs = UNDO_DURATION_MS } = options;
  return useToastStore.getState().show(message, {
    action: { label: "실행취소", onAction: onUndo },
    durationMs,
  });
}
