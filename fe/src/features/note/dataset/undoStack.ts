import { useCallback, useRef, useState } from "react";

/** 되돌리기/다시 실행 한 단계 — 서로 역인 두 썽크. undo·redo 모두 비동기(REST) 가능. */
export interface UndoEntry {
  undo: () => void | Promise<void>;
  redo: () => void | Promise<void>;
}

/**
 * 표 전용 되돌리기 스택(#932). 표의 행/셀 변경은 서버 REST라 에디터(ProseMirror)의 undo
 * 히스토리에 잡히지 않는다 — 그래서 각 변경이 자신의 역연산을 {@link UndoEntry}로 push하고
 * Cmd+Z가 그걸 실행한다. 세션 메모리라 새로고침하면 사라진다.
 *
 * <p>새 작업이 들어오면 redo 스택을 버린다(되돌린 뒤 다른 편집을 하면 분기된 미래는 무효).
 * undo/redo가 비동기라 진행 중 재진입을 막는다(연속 Cmd+Z가 겹쳐 순서가 꼬이지 않게).
 */
export function useUndoStack() {
  const undoStack = useRef<UndoEntry[]>([]);
  const redoStack = useRef<UndoEntry[]>([]);
  const running = useRef(false);
  // canUndo/canRedo가 UI(버튼 활성)에 반영되도록 스택이 바뀔 때 리렌더한다.
  const [, bump] = useState(0);
  const refresh = () => bump((n) => n + 1);

  const push = useCallback((entry: UndoEntry) => {
    undoStack.current.push(entry);
    redoStack.current = [];
    refresh();
  }, []);

  const undo = useCallback(async () => {
    if (running.current) return false;
    const entry = undoStack.current.pop();
    if (!entry) return false;
    running.current = true;
    try {
      await entry.undo();
      redoStack.current.push(entry);
    } finally {
      running.current = false;
      refresh();
    }
    return true;
  }, []);

  const redo = useCallback(async () => {
    if (running.current) return false;
    const entry = redoStack.current.pop();
    if (!entry) return false;
    running.current = true;
    try {
      await entry.redo();
      undoStack.current.push(entry);
    } finally {
      running.current = false;
      refresh();
    }
    return true;
  }, []);

  const clear = useCallback(() => {
    undoStack.current = [];
    redoStack.current = [];
    refresh();
  }, []);

  return {
    push,
    undo,
    redo,
    clear,
    canUndo: undoStack.current.length > 0,
    canRedo: redoStack.current.length > 0,
  };
}
