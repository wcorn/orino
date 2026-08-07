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
  /**
   * 액션 없이 그냥 사라졌을 때 실행할 일. 실행취소 스낵바에서
   * "취소하지 않았으므로 이제 진짜 반영한다"를 담는다.
   */
  onExpire?: () => void;
}

const AUTO_DISMISS_MS = 3500;
/** 실행취소를 줄 때의 기본 지속시간. 되돌릴 여유와 대기 사이의 절충값이다. */
export const UNDO_DURATION_MS = 5000;

let counter = 0;

interface ShowOptions {
  variant?: ToastVariant;
  action?: ToastAction;
  durationMs?: number;
  onExpire?: () => void;
}

interface ToastState {
  toasts: ToastItem[];
  show: (message: string, options?: ShowOptions) => string;
  /** 타이머 만료로 닫기 — `onExpire`가 있으면 실행한다. */
  expire: (id: string) => void;
  /** 사용자가 닫거나 액션을 눌러 닫기 — `onExpire`는 실행하지 않는다. */
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
      onExpire,
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
          onExpire,
        },
      ],
    }));
    setTimeout(() => get().expire(id), durationMs);
    return id;
  },

  expire: (id) => {
    const item = get().toasts.find((t) => t.id === id);
    if (!item) return;
    get().dismiss(id);
    item.onExpire?.();
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
  /**
   * 되돌리지 않고 시간이 다 됐을 때 실행. 삭제 요청을 이 시점까지 미뤄 두면
   * 실행취소 시 요청 자체가 나가지 않는다(서버에 복원 API를 두지 않는 이유).
   */
  onCommit?: () => void;
  durationMs?: number;
}

/**
 * 실행취소 스낵바. 5초 안에 누르면 `onUndo`, 그냥 지나가면 `onCommit`이 실행된다.
 * 낙관적으로 화면만 먼저 바꾸고 실제 요청은 `onCommit`에서 보내는 쓰임을 전제로 한다.
 */
export function toastUndo(message: string, options: UndoOptions) {
  const { onUndo, onCommit, durationMs = UNDO_DURATION_MS } = options;
  return useToastStore.getState().show(message, {
    action: { label: "실행취소", onAction: onUndo },
    durationMs,
    onExpire: onCommit,
  });
}
