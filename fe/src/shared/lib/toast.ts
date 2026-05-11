import { create } from "zustand";

export type ToastVariant = "info" | "success" | "error";

export interface ToastItem {
  id: string;
  message: string;
  variant: ToastVariant;
}

interface ToastState {
  toasts: ToastItem[];
  show: (message: string, variant?: ToastVariant) => void;
  dismiss: (id: string) => void;
}

const AUTO_DISMISS_MS = 3500;

let counter = 0;

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],
  show: (message, variant = "info") => {
    const id = `t-${Date.now()}-${++counter}`;
    set((state) => ({ toasts: [...state.toasts, { id, message, variant }] }));
    setTimeout(() => get().dismiss(id), AUTO_DISMISS_MS);
  },
  dismiss: (id) =>
    set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}));

export function toast(message: string, variant: ToastVariant = "info") {
  useToastStore.getState().show(message, variant);
}
