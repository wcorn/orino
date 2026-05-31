import { useCallback, useEffect, useRef, useState } from "react";

import { type NoteUpdateRequest, updateNote } from "../api/notes";

export type SaveStatus = "idle" | "saving" | "saved" | "error";

interface UseAutoSaveNoteOptions {
  /** 저장 성공 시 호출. 방금 반영된 patch를 전달 (예: title 변경 시 트리 invalidate). */
  onSaved?: (patch: NoteUpdateRequest) => void;
}

interface UseAutoSaveNoteResult {
  status: SaveStatus;
  savedAt: Date | null;
  schedule: (patch: NoteUpdateRequest) => void;
  flush: () => void;
  retry: () => void;
}

const DEBOUNCE_MS = 2000;

/**
 * noteId의 노트에 대해 title/content 부분 변경을 2초 debounce로 PATCH.
 * 같은 debounce 창의 여러 schedule은 병합되어 1회 호출된다.
 */
export function useAutoSaveNote(
  noteId: number | null,
  options: UseAutoSaveNoteOptions = {},
): UseAutoSaveNoteResult {
  const [status, setStatus] = useState<SaveStatus>("idle");
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const pendingRef = useRef<NoteUpdateRequest | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastFailedRef = useRef<NoteUpdateRequest | null>(null);
  const noteIdRef = useRef(noteId);
  noteIdRef.current = noteId;
  const onSavedRef = useRef(options.onSaved);
  onSavedRef.current = options.onSaved;

  const save = useCallback(async (patch: NoteUpdateRequest) => {
    const id = noteIdRef.current;
    if (id == null) return;
    setStatus("saving");
    try {
      const res = await updateNote(id, patch);
      setSavedAt(new Date(res.updatedAt));
      setStatus("saved");
      lastFailedRef.current = null;
      onSavedRef.current?.(patch);
    } catch {
      lastFailedRef.current = patch;
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
    if (pending) {
      void save(pending);
    }
  }, [save]);

  const schedule = useCallback(
    (patch: NoteUpdateRequest) => {
      pendingRef.current = { ...pendingRef.current, ...patch };
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      timerRef.current = setTimeout(fire, DEBOUNCE_MS);
    },
    [fire],
  );

  const flush = useCallback(() => {
    fire();
  }, [fire]);

  const retry = useCallback(() => {
    const failed = lastFailedRef.current;
    if (failed) {
      void save(failed);
    }
  }, [save]);

  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, []);

  return { status, savedAt, schedule, flush, retry };
}
