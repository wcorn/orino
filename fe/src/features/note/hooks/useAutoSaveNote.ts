import { useCallback, useEffect, useRef, useState } from "react";

import { type NoteContent, putNote } from "../api/notes";

export type SaveStatus = "idle" | "saving" | "saved" | "error";

interface UseAutoSaveNoteResult {
  status: SaveStatus;
  savedAt: Date | null;
  schedule: (content: NoteContent) => void;
  flush: () => void;
  retry: () => void;
}

const DEBOUNCE_MS = 2000;

export function useAutoSaveNote(materialId: number): UseAutoSaveNoteResult {
  const [status, setStatus] = useState<SaveStatus>("idle");
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const pendingRef = useRef<NoteContent | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastFailedRef = useRef<NoteContent | null>(null);

  const save = useCallback(
    async (content: NoteContent) => {
      setStatus("saving");
      try {
        const res = await putNote(materialId, content);
        setSavedAt(new Date(res.updatedAt));
        setStatus("saved");
        lastFailedRef.current = null;
      } catch {
        lastFailedRef.current = content;
        setStatus("error");
      }
    },
    [materialId],
  );

  const flush = useCallback(() => {
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
    (content: NoteContent) => {
      pendingRef.current = content;
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      timerRef.current = setTimeout(() => {
        timerRef.current = null;
        const pending = pendingRef.current;
        pendingRef.current = null;
        if (pending) {
          void save(pending);
        }
      }, DEBOUNCE_MS);
    },
    [save],
  );

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
